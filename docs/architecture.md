# Architecture

## Goals

1. Mutation test open source Gosu.
2. Keep the engine independent of Gosu, so that the compiler and the test runner can be replaced
   by other adapters (another build layout, another test framework, another Gosu runtime).
3. Never modify the project under test and keep mutants isolated from each other.

## Feasibility findings (phase 1)

Everything below was verified by running it, on macOS with Temurin JDK 17.0.2 and 21.0.13.

| Question | Finding |
| --- | --- |
| Which Gosu version? | `org.gosu-lang.gosu:gosu-core:1.18.14` from Maven Central (latest release at the time). Compiled for Java 11. |
| Can Gosu be compiled programmatically? | Yes. `gw.lang.gosuc.cli.CommandLineCompiler` (in `gosu-core-api`) is the `gosuc` command line compiler. It takes `-sourcepath`, `-classpath`, `-d` and source files, exits with 0 on success and 1 on compile errors, and prints `gosuc completed with N warnings and M errors`. About 3 seconds for a two class project, mostly JVM and type system start-up. |
| Can Gosu tests be run automatically? | Yes. Gosu classes compile to ordinary class files (class file version 52, with `SourceFile` and line number tables). A Gosu class with JUnit 5 `@Test` functions is discovered and run by the JUnit Platform like a Java test. |
| Is the parser accessible? | Yes, through the public API: `GosuCompiler.initializeGosu(...)`, `TypeSystem.getByFullNameIfValid(name)`, `IGosuClass.getClassStatement()`, then `IParsedElement` / `IParseTree`. Every node has an exact character offset and length, a line and a column, its enclosing function name and, for expressions, its type. Comments and string literals are not expression nodes, so they are never mutated. |
| Class loading constraints? | The Gosu type system is a JVM wide singleton. It can be initialized and uninitialized repeatedly in one JVM, but two projects cannot be loaded at once. |
| Line endings? | The parser drops carriage returns, so its offsets do not match a file with Windows (CRLF) line endings. `GosuMutationGenerator` translates parser offsets to file offsets and keeps the original line endings. |
| Surprises? | Gosu resolves the annotations of every Java class it reads. JUnit's classes are annotated with `@API` from `apiguardian-api`, which Gradle treats as compile only, so it has to be added explicitly or Gosu fails with `ClassNotFoundException: org.apiguardian.api.API`. |

### Java version

The brief asked for Java 21 "if compatible". It is, so the build defaults to a **Java 21**
toolchain. Gosu itself is compiled for Java 11.

| JDK | Result |
| --- | --- |
| 21 (Temurin 21.0.13) | Works. All tests pass and the sample run gives the same result as on 17. This is the default. |
| 17 (Temurin 17.0.2) | Works. All tests pass. Build with `-PjavaVersion=17`. |
| 22, 23 | Not tried. The ASM 9.7 bundled with Gosu reads class files up to Java 23, so they may work. |
| 25 (OpenJDK 25.0.2) | **Does not work.** The Gosu compiler fails with `Unsupported class file major version 69` while reading the JDK's own classes. The framework reports it as a failed baseline. |

The class files are always compiled with `--release 17`, so a distribution built with the Java 21
toolchain still runs on Java 17.

Gradle itself may run on any JDK it supports (it ran on JDK 25 here); only the toolchain matters,
and Gradle downloads it when it is missing (foojay resolver plugin in `settings.gradle`).

The JDK that runs the `gosu-mutation` command is also the JDK used for the child JVMs, so it must be
one that Gosu supports.

### Tools that were considered and not used

- **Gosu Gradle plugin** (`org.gosu-lang.gosu` 8.2.2). Not needed: the framework drives `gosuc`
  directly, which keeps it independent of the build tool of the project under test. Its
  compatibility with Gradle 9 was not checked.
- **`gosu-test-api`** (`gw.test.TestClass`). Not validated. Tests are expected to be JUnit Platform
  tests.
- **PIT**. Studied as a reference for concepts (mutant states, coverage based skipping, isolation
  in child JVMs, the test strength figure). Not used as a library: PIT mutates bytecode, see below.

## Source mutation or bytecode mutation?

| | Source level (chosen) | Bytecode level (PIT style) |
| --- | --- | --- |
| How | Edit the `.gs` text at offsets given by the Gosu parser, recompile. | Rewrite the compiled class with ASM, no recompilation. |
| Speed | Slow: one compilation per mutant. | Fast. |
| Reports | Shows the exact original and mutated source line. | Must map bytecode back to source; Gosu generates synthetic code (properties, blocks, enhancements, null safe operators, coercions) that makes this unreliable. |
| Invalid mutants | Caught by the compiler and reported as `COMPILATION_ERROR`. | Can produce bytecode that no Gosu source could produce (false, "unreal" mutants). |
| Other Gosu runtimes | Works wherever Gosu source can be compiled. | Requires controlling how each runtime loads classes. |
| Effort | Small: the parser API gives everything needed. | Large: a Gosu aware bytecode pattern catalogue. |

**Decision:** source level mutation driven by the Gosu syntax tree. It is the simplest approach that
gives a real end to end flow, the most faithful to what a developer would write, and the most
likely to carry over to other Gosu runtimes. The cost is execution time; see "Performance" for the planned
mitigations. A bytecode back end could be added later behind the same ports.

## Modules

```
mutation-cli ──► mutation-gosu ──┐
     │      ──► mutation-runner ─┼──► mutation-core
     └────────► mutation-report ─┘
```

`mutation-core` depends on nothing. The other modules depend on `mutation-core` only (and
`mutation-cli` wires them together). `mutation-runner` uses `mutation-gosu` in its tests, to run
the whole pipeline for real.

### mutation-core

- Model: `MutationDescriptor`, `MutationResult`, `MutationStatus`, `MutationScore`,
  `MutationConfiguration`, `MutationRunReport`, `MutationOperator`.
- `MutationEngine`: baseline, generation, parallel evaluation, aggregation.
- Ports (`io.gosumutation.core.port`):

| Port | Responsibility | Implementation today |
| --- | --- | --- |
| `MutationGenerator` | source analysis and mutant generation | `GosuMutationGenerator` |
| `ProjectCompiler` | compile a source tree | `GosuCompilerAdapter` |
| `TestRunner` | run the tests of a compiled project | `GosuTestRunner` |
| `BaselineRunner` | compile and test the unmodified project, with coverage | `IsolatedBaselineRunner` |
| `MutantExecutor` | evaluate one mutant in isolation | `MutationExecutor` |
| `CoverageData` | which lines the baseline executed | `JacocoCoverage` |
| `ReportGenerator` | persist results in one format | `HtmlReportGenerator`, `JsonReportGenerator` |
| `MutationListener` | progress events | `ConsoleProgressListener` |

- `io.gosumutation.core.process`: `ProcessIsolation` (run a process with a hard timeout, kill the
  whole process tree, capture output through a file) and `ForkedJvm` (build a child JVM command
  line using a Java argument file).

### mutation-gosu

- `GosuSourceAnalyzer`: initializes the Gosu type system for a set of source roots and returns the
  syntax tree of a class.
- `GosuMutationOperator` and `operators/*`: one class per operator, discovered with
  `ServiceLoader`. `GosuSource` maps tree nodes back to text.
- `GosuMutationGenerator`: walks each tree, asks every enabled operator for edits, validates each
  edit against the file on disk and builds the descriptors.
- `GosuCompilerAdapter`: runs `gosuc` in a child JVM.

### mutation-runner

- `ProjectWorkspace`: temporary copy of the source and test directories, with one mutation applied.
- `MutationExecutor`: workspace, compile, test, classify.
- `IsolatedBaselineRunner`: same flow without a mutation, plus coverage.
- `GosuTestRunner` and `worker/JUnitWorkerMain`: run JUnit Platform tests in a child JVM and report
  the failed tests through a result file.
- `JacocoCoverage`: reads the JaCoCo data of the baseline run.

### mutation-report, mutation-cli

Report writers (Jackson for JSON, a single self contained HTML page that embeds the same JSON) and
the picocli command line. `gosu-mutation class` copies one class (and its tests) from another code
base into a trial project and runs `run` on it; `run` writes `baseline-failure.txt` next to the
reports when the baseline fails, for tools that need the reason.

### intellij-plugin

A separate Gradle build (it downloads IntelliJ IDEA to compile against) that includes the main build
and bundles the command line tool's jars. The plugin does not parse Gosu: it works with file paths,
runs `gosu-mutation class` as a child process with the IDE's Java runtime, and reads the report and
`baseline-failure.txt` it leaves behind. The AI stub request is assembled from the compiler errors,
the class, its test and `docs/stubbing-guide.md`; the plugin hands it to the user (clipboard and an
editor tab) and does not call any AI service itself.

How the plugin is organised:

| Piece | Classes | Needs the IDE? |
| --- | --- | --- |
| Finding tests, building the command line, trial paths | `TestFileLocator`, `TestSelection`, `MutationCommand`, `TrialPaths` | no |
| Reading results | `Json` (a small reader, so no JSON library is needed), `MutationResults` | no |
| Equivalent mutants | `EquivalentMutantsFile` (`<trial>/gosu-mutation-equivalent.txt`) | no |
| AI requests | `StubRequestBuilder`, `TestRequestBuilder`, `StubRequestStatus`, `BaselineErrors` | no |
| Running | `MutationRunner` (process, Run window, `last-run.log`, notifications) | yes |
| Showing results | `MutationResultsService` (project service), `MutationResultsPanel` (tool window), `EditorMarkers` (gutter icons) | yes |
| Actions and settings | `*Action`, `GosuMutationSettings`, `GosuMutationConfigurable`, `AiRequests` | yes |

After a run, `MutationResultsService` reads `mutation-report.json` of the trial, applies the
equivalent list (so marking shows immediately, before the next run) and notifies the tool window and
the editor markers. Markers are placed only on lines whose text still equals the mutated line in the
report, so stale results never point at the wrong code.

### Differences from the layout in the original brief

| Brief | Here | Why |
| --- | --- | --- |
| `ProcessIsolation` in `mutation-runner` | `mutation-core` (`core.process`) | The Gosu compiler adapter (in `mutation-gosu`) also needs it, and `mutation-gosu` should not depend on `mutation-runner`. It uses only the JDK, so the core stays dependency free. |
| `TestRunner` in `mutation-runner` | interface in `mutation-core`, implementation in `mutation-runner` | The engine and executor depend on the abstraction. |
| one `MutationOperator` | `MutationOperator` (core, identity only) and `GosuMutationOperator` (Gosu, locates mutation points) | Finding mutation points needs the Gosu syntax tree, which the core must not know. |
| `MutationExecutor` | port `MutantExecutor` in core, class `MutationExecutor` in runner | Same reason as `TestRunner`. |
| not listed | `BaselineRunner`, `CoverageData`, `ProjectWorkspace`, `JacocoCoverage`, `JUnitWorkerMain` | Needed for the baseline check, `NO_COVERAGE` and isolation. |

## The run, step by step

1. **Baseline.** Copy sources and tests to a temporary workspace, compile with `gosuc`, run the
   tests with the JaCoCo agent. If compilation fails, a test fails or no test is found, the run
   stops with exit code 1: mutation results would be meaningless.
2. **Generate.** Parse every selected `.gs` file of the *original* project (read only) and collect
   the mutants. Sort them by file, offset, operator.
3. **Evaluate**, `--threads` mutants at a time. For each mutant:
   1. If the user listed the mutant's id as equivalent: `EQUIVALENT`, done.
   2. If the baseline coverage says the line holds code that no test executed: `NO_COVERAGE`, done.
   3. Create a new workspace and apply the mutation to the copy. The copy must contain the expected
      original text at the recorded offset, otherwise the mutant is a `RUN_ERROR`.
   4. Compile. Compile errors: `COMPILATION_ERROR`. Timeout: `TIMED_OUT`. Compiler crash:
      `RUN_ERROR`.
   5. Run all tests. Failures: `KILLED`, with the names of the failing tests. All pass: `SURVIVED`.
      Timeout: `TIMED_OUT`. Worker crash: `RUN_ERROR`.
   6. Delete the workspace.
4. **Report.** Compute the score and write the reports.

## Isolation guarantees

- The project directory is only read. All writes go to `java.io.tmpdir` and to the report
  directory. Tests assert that the sources are unchanged after a run.
- One workspace per mutant, so mutants cannot see each other, even in parallel.
- Compilation and tests run in child JVMs: a mutant that corrupts static state, exhausts memory or
  calls `System.exit` cannot affect the framework or the next mutant.
- Timeouts kill the child process and its descendants.
- An exception while evaluating a mutant becomes `RUN_ERROR` for that mutant only.

## Scoring

```
mutation score          = killed / (killed + survived)               x 100
score including uncovered = killed / (killed + survived + noCoverage) x 100
```

Both are rounded to one decimal and are 0 when the denominator is 0 (the report shows "n/a" for
the first). `TIMED_OUT`, `COMPILATION_ERROR`, `RUN_ERROR`, `NOT_RUN` and `EQUIVALENT` are never in either
formula and are always listed separately. A timeout is not counted as killed because the framework
cannot tell an infinite loop caused by the mutation from a slow machine.

## Determinism

- Mutant id = first 12 hex digits of SHA-256 over `relativePath | operatorId | startOffset |
  originalText | replacementText`. The same source always gives the same ids.
- Mutants and results are ordered by file, offset and operator, regardless of thread scheduling.
- Not deterministic: timings, the `generatedAt` timestamp, and `TIMED_OUT` results on a machine
  under heavy load.

## Coverage

The baseline runs with the JaCoCo agent. A mutant is skipped as `NO_COVERAGE` only when JaCoCo
reports that its line has code and none of it ran. Lines without line number information (for
example the second line of a multi line expression) are treated as covered, so their mutants are
executed rather than wrongly skipped. If coverage cannot be collected, every mutant is executed.

## Known limitations

- One full compilation and one full test run per covered mutant.
- No per test coverage, so no selection of the tests relevant to a mutant, and no fail fast: all
  tests run so that every killing test can be listed.
- Only `.gs` classes are mutated. `.gsx` enhancements are compiled but not mutated. `.gsp` programs
  and `.gst` templates are not supported.
- The timeout applies to compilation and to the test run separately.
- Inner classes are reported with the name Gosu gives them; class filters match top level classes.
- Equivalent mutants are not detected.
- Verified on macOS, Linux and Windows with JDK 21, and on macOS and Linux with JDK 17. Other
  combinations have not been tried.

## Performance ideas (not implemented)

1. Compile only the mutated file against the baseline class files instead of the whole project.
2. Keep a warm Gosu compiler process per worker thread instead of starting a JVM per mutant.
3. Record coverage per test and run only the tests that reach the mutated line, stopping at the
   first failure.
4. Reuse results from a previous run for unchanged files (the ids are already stable).
