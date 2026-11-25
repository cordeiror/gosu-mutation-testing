# Gosu Mutation Testing

An open source mutation testing framework for the [Gosu](https://gosu-lang.github.io/) programming
language.

It makes small changes ("mutants") to your Gosu code, one at a time, and runs your tests against
each one. A mutant that makes a test fail is **killed**; a mutant that no test notices **survived**
and points at behaviour your tests do not actually check, even when line coverage says 100%.

- Works on open source Gosu.
- Mutates with the real Gosu parser, so comments and string literals are never touched.
- Never modifies your project: every mutant is compiled and tested in a temporary copy.
- Produces a self contained HTML report and a JSON report.

Status: MVP (0.1.0). See [Limitations](#limitations).

## Requirements

- A JDK to start Gradle. The build compiles and tests with Java 21 through a Gradle toolchain,
  which Gradle downloads automatically if no JDK 21 is installed. Java 17 also works:
  add `-PjavaVersion=17` to the Gradle commands.
- To run the installed `gosu-mutation` command: Java 17 or Java 21 (the class files target
  Java 17 whichever toolchain built them). Java 25 does not work, because Gosu 1.18.14 cannot read
  its class files. The command uses `JAVA_HOME` if set, otherwise the `java` on your `PATH`.
- Internet access on the first build, to download Gosu 1.18.14 and the other dependencies from
  Maven Central.

Nothing else: Gosu itself and JUnit are bundled with the tool.

## Quick start

```bash
./gradlew build
```

```bash
./gradlew :mutation-cli:mutationSample
```

The second command mutation tests `sample-gosu-project` and writes
`sample-gosu-project/build/reports/gosu-mutation/mutation-report.html` (and `.json`).

Expected result for the sample:

```
Mutants generated : 18
Killed            : 9
Survived          : 5
No coverage       : 4
Mutation score    : 64.3%  (killed / (killed + survived))
```

`EligibilityService` is fully tested and all 5 of its mutants are killed. `DiscountCalculator` has
intentionally incomplete tests: `discountFor` has 100% line coverage, yet 5 of its 9 mutants
survive, and the untested `isBulkOrder` accounts for the 4 mutants without coverage.

## Installing the command line tool

```bash
./gradlew :mutation-cli:installDist
```

This creates `mutation-cli/build/install/gosu-mutation/`; add its `bin` directory to your `PATH`.
`./gradlew :mutation-cli:distZip` builds a zip of the same thing.

## Usage

```bash
gosu-mutation run --project ./sample-gosu-project
```

```bash
gosu-mutation run --project ./sample-gosu-project --class EligibilityService
```

```bash
gosu-mutation report --project ./sample-gosu-project --format html
```

```bash
gosu-mutation operators
```

### `gosu-mutation run`

| Option | Default | Meaning |
| --- | --- | --- |
| `-p`, `--project <dir>` | `.` | Project root. |
| `--source-dir <dir>` | `src/main/gosu` | Production sources, relative to the project. Repeatable. |
| `--test-dir <dir>` | `src/test/gosu` | Test sources, relative to the project. Repeatable. |
| `-cp`, `--classpath <path>` | none | Extra jars/directories the project needs. Gosu, JUnit 5 and JUnit 4 are already provided. |
| `-c`, `--class <name>` | all | Classes to mutate: simple or fully qualified names, `*` allowed, comma separated or repeated. |
| `-o`, `--operators <id>` | all | Mutation operators to use, comma separated. |
| `-t`, `--timeout <seconds>` | `60` | Limit for compiling and for testing each mutant. |
| `-j`, `--threads <n>` | half the processors, max 4 | Mutants evaluated in parallel. |
| `-r`, `--report-dir <dir>` | `<project>/build/reports/gosu-mutation` | Where the reports go. |
| `-f`, `--format <format>` | `html,json` | Report formats. |
| `--threshold <percent>` | none | Exit with code 3 when the mutation score is lower. |
| `-q`, `--quiet` | off | No progress log. |

Exit codes: `0` success, `1` the baseline failed or the run could not complete, `2` invalid
arguments, `3` score below `--threshold`.

Progress is logged to standard error, one structured line per event:

```
15:46:08.294 INFO  mutant_finished n=2/18 id=6a1eb4d48bed status=KILLED operator=LOGICAL location=sample.DiscountCalculator:12 ms=4879
```

### `gosu-mutation report`

Rebuilds reports from the `mutation-report.json` of an earlier run, without running anything.
Options: `--input <file>`, `--project <dir>`, `--report-dir <dir>`, `--format html,json`.

### Mutation testing one class from another code base

To try a single class that lives in a larger code base, keep a separate *trial project* with the
tests for that class and let Gradle copy the class into it:

```bash
./gradlew :mutation-cli:mutateClass -PsourceFile=<path to the .gs file> -PtrialDir=<trial project dir>
```

On Windows (cmd or PowerShell):

```bat
gradlew.bat :mutation-cli:mutateClass -PsourceFile=C:\path\to\MyClass.gs -PtrialDir=D:\trials\my-class
```

The trial project is created by the task and marked with a `.gosu-mutation-trial` file; on every
run its copies of the class and tests are replaced, so nothing from an earlier run is left behind.
Files in a trial folder the task did not create are never deleted.

The task reads the `package` of the file, copies it to
`<trialDir>/src/main/gosu/<package path>/` and mutates only that class. Original files are only read.

| Property | Required | Meaning |
| --- | --- | --- |
| `sourceFile` | yes | The Gosu class to mutate. |
| `trialDir` | yes | The trial project. Created if missing. |
| `testFile` | no | One or more test classes or folders, separated by `;`. Folders are searched recursively for Gosu files (`.gs`, `.gsx`), so a whole test folder (with its helper classes) can be given. Each file is copied to `<trialDir>/src/test/gosu/<package path>/`. Without it, the tests must already be in `<trialDir>/src/test/gosu`. |
| `stubsDir` | no | Folder with minimal stand-ins for types the class needs but that are not available outside its code base. Compiled with the tests, never mutated. Default: `<trialDir>/src/stubs/gosu` when it exists. |
| `mutationArgs` | no | Other `run` options, e.g. `-PmutationArgs="--threads 2 --timeout 120"`. |

Example with every input given explicitly (Windows):

```bat
gradlew.bat :mutation-cli:mutateClass -PsourceFile=C:\code\src\my\pkg\MyClass.gs -PtestFile=C:\code\test\my\pkg\MyClassTest.gs -PstubsDir=D:\trials\stubs -PtrialDir=D:\trials\my-class
```

How to write those stand-ins, with verified recipes for enum-like types, test base classes and
helpers: [docs/stubbing-guide.md](docs/stubbing-guide.md).

Keep trial projects outside this repository so code from another code base is never committed here.
Stubs replace real behaviour with assumptions, so results are only as accurate as those assumptions.

## IntelliJ IDEA plugin

`intellij-plugin/` contains a plugin for IntelliJ IDEA 2025.3 (build 253). Right-click a `.gs`
file, in the Project view or in the editor, and choose **Run Mutation Testing**:

1. The plugin finds the test of the class with the test folder mappings from the settings
   (`Foo.gs` is matched with `FooTest.gs`). When there is none, it asks you to choose the tests:
   any mix of files and folders (folders are searched recursively). The choice is remembered per
   class; **Run Mutation Testing with Chosen Tests...** changes it.
2. It runs `gosu-mutation class` for that class in a trial project of its own, and shows the output
   in the Run window.
3. At the end it opens the HTML report.

When the class or its test does not compile because they use types from their original code base,
the plugin offers **Build AI Stub Request**: it puts together the compiler errors, the class, the
test and the [stubbing guide](docs/stubbing-guide.md), copies that request to the clipboard and
opens it in an editor tab. Paste it into an AI assistant that can write files (for example GitHub
Copilot Chat in agent mode), let it create the stubs, and run again.

The plugin bundles the command line tool and runs it with the IDE's own Java runtime, so nothing
else has to be installed.

Build the plugin (the first build downloads IntelliJ IDEA, about 1.5 GB):

```bash
./gradlew -p intellij-plugin buildPlugin
```

Install `intellij-plugin/build/distributions/gosu-mutation-intellij-0.1.0.zip` with
**Settings | Plugins | ⚙ | Install Plugin from Disk...**, then review
**Settings | Tools | Gosu Mutation Testing**:

| Setting | Default | Meaning |
| --- | --- | --- |
| Trial projects folder | `~/gosu-mutation-trials` | One trial project per class is created here. |
| Stubs folder | empty | Stand-in types shared by all classes. Empty: `<trial>/src/stubs/gosu`. |
| Test folder mappings | `src/main/gosu=src/test/gosu` | `source=test` folder pairs separated by `;`. |
| Java home | empty | JDK 17 or 21 that runs the tool. Empty: the IDE's runtime. |
| Tool jars folder | empty | Only to use another build of the tool. |
| Extra run options | empty | Other `run` options, e.g. `--threads 2 --timeout 120`. |

Plugin tests (`./gradlew -p intellij-plugin test`) run the bundled tool for real and start a
headless IntelliJ IDEA to check the actions and settings; `./gradlew -p intellij-plugin verifyPlugin`
checks binary compatibility with IntelliJ IDEA 2025.3.4.

## Writing tests the tool can run

Tests are Gosu classes discovered by the JUnit Platform. Use JUnit 5 annotations:

```gosu
package sample

uses org.junit.jupiter.api.Assertions
uses org.junit.jupiter.api.Test

class EligibilityServiceTest {
  @Test
  function exactlyEighteenIsEligible() {
    Assertions.assertTrue(new EligibilityService().isEligible(18, true))
  }
}
```

## What the results mean

| Status | Meaning | In the score? |
| --- | --- | --- |
| `KILLED` | At least one test failed against the mutant. | yes |
| `SURVIVED` | The mutant compiled and every test passed. | yes |
| `NO_COVERAGE` | No test executed the mutated line in the baseline run, so the mutant was not run. | no |
| `TIMED_OUT` | Compilation or tests exceeded the timeout and were killed. | no |
| `COMPILATION_ERROR` | The mutated code did not compile. | no |
| `RUN_ERROR` | Infrastructure failure while evaluating the mutant. | no |
| `NOT_RUN` | The run was interrupted before the mutant was evaluated. | no |

**Mutation score = killed / (killed + survived) x 100**, rounded to one decimal.

Only mutants that compiled and were run to completion are in the denominator. Timeouts, compilation
errors and infrastructure errors are never counted as killed; they are listed separately. The
report also shows a second figure, *score including uncovered* = killed / (killed + survived +
no coverage), which penalizes untested code.

## Mutation operators

| Id | Example |
| --- | --- |
| `RELATIONAL_BOUNDARY` | `>=` to `>`, `<=` to `<`, `>` to `>=`, `<` to `<=` |
| `EQUALITY` | `==` to `!=`, `!=` to `==` |
| `LOGICAL` | `and` to `or`, `or` to `and` (also `&&`, `\|\|`) |
| `BOOLEAN_LITERAL` | `true` to `false`, `false` to `true` |
| `NUMERIC_LITERAL` | `10` to `11` |
| `BOOLEAN_RETURN` | `return expression` to `return true` and `return false` |

Details and how to add your own: [docs/mutation-operators.md](docs/mutation-operators.md).

## Project layout

| Module | Role |
| --- | --- |
| `mutation-core` | Engine, model, scoring and the ports (interfaces). No dependencies. |
| `mutation-gosu` | Gosu parser based analysis, the operators, the Gosu compiler adapter. |
| `mutation-runner` | Isolated workspaces, mutant execution, JUnit test runner, coverage. |
| `mutation-report` | HTML and JSON reports. |
| `mutation-cli` | The `gosu-mutation` command. |
| `intellij-plugin` | IntelliJ IDEA plugin (separate Gradle build that bundles the command). |
| `sample-gosu-project` | Demo Gosu project (not a Gradle module). |

More in [docs/architecture.md](docs/architecture.md).

## Limitations

- Verified with Gosu 1.18.14 on macOS, Linux and Windows with Java 21, and on macOS and Linux with
  Java 17 (see the CI workflow). Java 25 does not work.
- Every covered mutant triggers a full recompilation of the project and a full test run (a few
  seconds each on the sample). Fine for small projects, slow for large ones.
- Coverage is line level and for the whole suite. There is no per test selection yet.
- Only Gosu classes (`.gs`) are mutated. Enhancements (`.gsx`) are compiled but not mutated;
  programs (`.gsp`) and templates (`.gst`) are not supported.
- Tests must be discoverable by the JUnit Platform. JUnit 5 is tested; the JUnit 4 engine is bundled
  but not covered by this project's own tests. Gosu's `gw.test.TestClass` is not supported.
- Equivalent mutants (mutations that cannot change behaviour) are not detected and show up as
  survivors.

## Development

```bash
./gradlew build
```

runs every test, including integration tests that start the real Gosu compiler and run real Gosu
tests in child JVMs. Guidance for AI coding agents is in [AGENTS.md](AGENTS.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
