# Guidance for AI coding agents

This repository is a mutation testing framework for Gosu. Read `docs/architecture.md` before making
structural changes.

## Commands

- Build and run every test: `./gradlew build` (Java 21 toolchain; `-PjavaVersion=17` for Java 17)
- One module: `./gradlew :mutation-gosu:test`
- Run the tool on the demo project: `./gradlew :mutation-cli:mutationSample`
- Install the CLI locally: `./gradlew :mutation-cli:installDist`

The integration tests start child JVMs (Gosu compiler, JUnit) and take about a minute in total.

## Rules that must hold

1. **`mutation-core` has no dependencies.** No Gosu, JUnit (in main code), Gradle API or any other
   library. It only defines the model, the engine and the ports.
2. **Only open source Gosu.** No proprietary platform dependencies, names or code in this
   repository. Code taken from users' own code bases (classes, tests, stubs) stays outside it.
3. **Never modify the project under test.** Mutations are applied to copies in a temporary
   `ProjectWorkspace`. Tests assert that the original sources are byte for byte unchanged; keep
   those assertions.
4. **Do not turn timeouts, compilation errors or run errors into "killed".** The mutation score is
   `killed / (killed + survived)`. If you change scoring, update `MutationScore`, the README and the
   HTML template together.
5. **Do not mock the Gosu integration.** Tests of the parser, compiler and test runner use the real
   Gosu toolchain. In-memory stand-ins are acceptable only for testing `MutationEngine` through its
   ports.
6. **Do not claim something works without running it.** Run `./gradlew build` after each change and
   report failures as they are.
7. **Determinism.** Mutant ids are a hash of file, operator, offset and texts. Mutants and results
   are ordered by file, offset, operator. Do not introduce ordering that depends on thread timing or
   hash iteration.

## Mutation testing a class from another code base

When a user asks to run the tool on a class from their own code base, use `gosu-mutation class` (or `./gradlew :mutation-cli:mutateClass`) and follow
[docs/stubbing-guide.md](docs/stubbing-guide.md) to create stand-ins for the types it needs.
Stubs and the user's code stay outside this repository: never commit them here.

## Where things go

- A new mutation operator: a class in `mutation-gosu/.../operators` implementing
  `GosuMutationOperator`, registered in
  `mutation-gosu/src/main/resources/META-INF/services/io.gosumutation.gosu.GosuMutationOperator`,
  with cases added to `GosuMutationGeneratorTest` and an entry in `docs/mutation-operators.md`. The
  engine does not change.
- A new way to run tests (another framework, another platform): implement
  `io.gosumutation.core.port.TestRunner`.
- A new compiler: implement `io.gosumutation.core.port.ProjectCompiler`.
- A new report format: implement `io.gosumutation.core.port.ReportGenerator` and add it to
  `ReportFormats` in `mutation-cli`.

## Things that are easy to get wrong

- The Gosu type system is a JVM wide singleton. `GosuSourceAnalyzer` serializes access with a lock
  and must always be closed. Compilation never happens in the framework JVM; it is always a child
  process.
- Locate operators through the syntax tree (`IParsedElement` and `IParseTree` offsets), not by
  searching text. `GosuSource.operatorBetween` looks only between the two operands and skips
  comments.
- Gosu resolves the annotations of the Java classes it reads. A dependency that is "compile only"
  for Java (for example `apiguardian-api`, used by JUnit) must be on the class path given to Gosu.
- Child JVMs get the framework's own `java.class.path`. Keep anything the Gosu compiler or the test
  worker needs on the runtime class path of `mutation-cli`.
- Gosu 1.18.14 does not run on Java 25 (`Unsupported class file major version 69`). Verified JDKs
  are 17 and 21; do not raise the toolchain without running the whole build on the new version.
- Code, identifiers and documentation are in English.
