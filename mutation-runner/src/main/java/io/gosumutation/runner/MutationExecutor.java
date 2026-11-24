package io.gosumutation.runner;

import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.MutationDescriptor;
import io.gosumutation.core.MutationResult;
import io.gosumutation.core.MutationStatus;
import io.gosumutation.core.port.MutantExecutor;
import io.gosumutation.core.port.ProjectCompiler;
import io.gosumutation.core.port.ProjectCompiler.CompilationRequest;
import io.gosumutation.core.port.ProjectCompiler.CompilationResult;
import io.gosumutation.core.port.TestRunner;
import io.gosumutation.core.port.TestRunner.TestExecutionRequest;
import io.gosumutation.core.port.TestRunner.TestExecutionResult;

import java.io.IOException;
import java.util.List;

/**
 * Evaluates one mutant: copy the project to a private workspace, apply the mutation to the copy,
 * compile it, run the tests and classify the outcome. Works with any {@link ProjectCompiler} and
 * {@link TestRunner}; it holds no state and is safe to use from several threads.
 */
public final class MutationExecutor implements MutantExecutor {

    private final ProjectCompiler compiler;
    private final TestRunner testRunner;

    public MutationExecutor(ProjectCompiler compiler, TestRunner testRunner) {
        this.compiler = compiler;
        this.testRunner = testRunner;
    }

    @Override
    public MutationResult execute(MutationDescriptor mutant, MutationConfiguration configuration) {
        long start = System.nanoTime();
        try (ProjectWorkspace workspace = ProjectWorkspace.create(configuration, mutant)) {
            CompilationResult compilation = compiler.compile(new CompilationRequest(workspace.sourceRoots(),
                    configuration.classpath(), workspace.classesDir(), workspace.workDir(), configuration.mutantTimeout()));
            switch (compilation.status()) {
                case FAILED:
                    return MutationResult.of(mutant, MutationStatus.COMPILATION_ERROR, elapsed(start), compilation.output());
                case TIMED_OUT:
                    return MutationResult.of(mutant, MutationStatus.TIMED_OUT, elapsed(start),
                            "Compilation exceeded " + configuration.mutantTimeout().toSeconds() + "s");
                case ERROR:
                    return MutationResult.of(mutant, MutationStatus.RUN_ERROR, elapsed(start), compilation.output());
                default:
                    break;
            }

            TestExecutionResult tests = testRunner.execute(new TestExecutionRequest(workspace.classesDir(),
                    configuration.classpath(), workspace.workDir(), configuration.mutantTimeout(), null));
            return switch (tests.status()) {
                case PASSED -> MutationResult.of(mutant, MutationStatus.SURVIVED, elapsed(start), "");
                case FAILED -> new MutationResult(mutant, MutationStatus.KILLED, tests.failedTests(), elapsed(start), "");
                case TIMED_OUT -> MutationResult.of(mutant, MutationStatus.TIMED_OUT, elapsed(start),
                        "Tests exceeded " + configuration.mutantTimeout().toSeconds() + "s");
                case ERROR -> MutationResult.of(mutant, MutationStatus.RUN_ERROR, elapsed(start), tests.output());
            };
        } catch (IOException | RuntimeException e) {
            return new MutationResult(mutant, MutationStatus.RUN_ERROR, List.of(), elapsed(start), e.toString());
        }
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
