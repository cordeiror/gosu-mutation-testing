# Gosu Mutation Testing

An open source mutation testing framework for the [Gosu](https://gosu-lang.github.io/) programming
language.

It makes small changes ("mutants") to your Gosu code, one at a time, and runs your tests against
each one. A mutant that makes a test fail is **killed**; a mutant that no test notices **survived**
and points at behaviour your tests do not actually check, even when line coverage says 100%.

- Works on plain open source Gosu. No platform dependency.
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
| `sample-gosu-project` | Demo Gosu project (not a Gradle module). |

More in [docs/architecture.md](docs/architecture.md). Plans for platform product, product
and product are in [docs/architecture.md](docs/architecture.md); nothing
platform specific is implemented.

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
