package io.gosumutation.core.port;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Runs the tests of an already compiled project.
 *
 * <p>This is the main extension point for other environments: {@code GosuTestRunner} runs JUnit
 * Platform tests of a standalone Gosu project; a {@code PlatformTestRunner} could start a
 * platform test server instead (see docs/architecture.md).
 */
public interface TestRunner {

    TestExecutionResult execute(TestExecutionRequest request);

    /**
     * @param classesDir   directory with the compiled production and test classes
     * @param classpath    project dependencies
     * @param workDir      scratch directory owned by the caller
     * @param timeout      limit after which the test process is killed
     * @param coverageFile when not null, line coverage data must be written to this file
     */
    record TestExecutionRequest(Path classesDir, List<Path> classpath, Path workDir, Duration timeout, Path coverageFile) {
        public TestExecutionRequest {
            classpath = List.copyOf(classpath);
        }
    }

    enum TestStatus { PASSED, FAILED, TIMED_OUT, ERROR }

    /**
     * @param status      overall outcome
     * @param testsRun    number of tests executed
     * @param failedTests display ids of the failed tests, e.g. {@code demo.FooTest.bar()}
     * @param output      console output or error description
     */
    record TestExecutionResult(TestStatus status, int testsRun, List<String> failedTests, String output, long durationMillis) {
        public TestExecutionResult {
            failedTests = List.copyOf(failedTests);
        }
    }
}
