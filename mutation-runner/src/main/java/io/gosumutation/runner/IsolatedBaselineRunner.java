package io.gosumutation.runner;

import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.port.BaselineResult;
import io.gosumutation.core.port.BaselineRunner;
import io.gosumutation.core.port.CoverageData;
import io.gosumutation.core.port.ProjectCompiler;
import io.gosumutation.core.port.ProjectCompiler.CompilationRequest;
import io.gosumutation.core.port.ProjectCompiler.CompilationResult;
import io.gosumutation.core.port.ProjectCompiler.CompilationStatus;
import io.gosumutation.core.port.TestRunner;
import io.gosumutation.core.port.TestRunner.TestExecutionRequest;
import io.gosumutation.core.port.TestRunner.TestExecutionResult;
import io.gosumutation.core.port.TestRunner.TestStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Compiles and tests an unmodified copy of the project, collecting line coverage. The baseline gets
 * a generous timeout (ten times the per mutant limit, at least five minutes) because it is the
 * reference run and nothing has been mutated yet.
 */
public final class IsolatedBaselineRunner implements BaselineRunner {

    private final ProjectCompiler compiler;
    private final TestRunner testRunner;

    public IsolatedBaselineRunner(ProjectCompiler compiler, TestRunner testRunner) {
        this.compiler = compiler;
        this.testRunner = testRunner;
    }

    @Override
    public BaselineResult run(MutationConfiguration configuration) {
        long start = System.nanoTime();
        Duration timeout = configuration.mutantTimeout().multipliedBy(10);
        if (timeout.compareTo(Duration.ofMinutes(5)) < 0) {
            timeout = Duration.ofMinutes(5);
        }
        try (ProjectWorkspace workspace = ProjectWorkspace.create(configuration, null)) {
            CompilationResult compilation = compiler.compile(new CompilationRequest(workspace.sourceRoots(),
                    configuration.classpath(), workspace.classesDir(), workspace.workDir(), timeout));
            if (compilation.status() != CompilationStatus.SUCCESS) {
                return BaselineResult.failure("the original project does not compile (" + compilation.status() + ")\n"
                        + compilation.output(), List.of(), elapsed(start));
            }

            Path coverageFile = workspace.workDir().resolve("baseline.exec");
            TestExecutionResult tests = testRunner.execute(new TestExecutionRequest(workspace.classesDir(),
                    configuration.classpath(), workspace.workDir(), timeout, coverageFile));
            if (tests.status() != TestStatus.PASSED) {
                String reason = switch (tests.status()) {
                    case FAILED -> "tests fail on the original project: " + String.join(", ", tests.failedTests());
                    case TIMED_OUT -> "tests of the original project exceeded " + timeout.toSeconds() + "s";
                    default -> "tests of the original project could not be run";
                };
                return BaselineResult.failure(reason + "\n" + tests.output(), tests.failedTests(), elapsed(start));
            }

            CoverageData coverage = CoverageData.UNAVAILABLE;
            if (Files.exists(coverageFile)) {
                try {
                    coverage = JacocoCoverage.load(coverageFile, workspace.classesDir());
                } catch (IOException | RuntimeException e) {
                    // Coverage is an optimization; without it every mutant is simply executed.
                    coverage = CoverageData.UNAVAILABLE;
                }
            }
            return new BaselineResult(true, tests.testsRun(), List.of(), elapsed(start), "", coverage);
        } catch (IOException | RuntimeException e) {
            return BaselineResult.failure("baseline could not be prepared: " + e, List.of(), elapsed(start));
        }
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
