package io.gosumutation.core.port;

import java.util.List;

/**
 * @param passed         the project compiled and every test passed
 * @param testsRun       number of tests executed
 * @param failedTests    tests that failed in the unmodified project
 * @param durationMillis compile + test time
 * @param message        explanation when {@code passed} is false
 * @param coverage       line coverage of the baseline test run
 */
public record BaselineResult(
        boolean passed,
        int testsRun,
        List<String> failedTests,
        long durationMillis,
        String message,
        CoverageData coverage) {

    public BaselineResult {
        failedTests = List.copyOf(failedTests);
        coverage = coverage == null ? CoverageData.UNAVAILABLE : coverage;
    }

    public static BaselineResult failure(String message, List<String> failedTests, long durationMillis) {
        return new BaselineResult(false, 0, failedTests, durationMillis, message, CoverageData.UNAVAILABLE);
    }
}
