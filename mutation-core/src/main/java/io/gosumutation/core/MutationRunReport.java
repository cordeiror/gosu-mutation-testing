package io.gosumutation.core;

import java.util.List;

/**
 * Complete, serializable result of a mutation run. It deliberately contains only plain values so
 * that report generators can persist and reload it without custom mapping.
 */
public record MutationRunReport(
        int schemaVersion,
        String generatedAt,
        String projectDir,
        List<String> sourceDirs,
        List<String> testDirs,
        long timeoutSeconds,
        int parallelism,
        List<String> operators,
        int baselineTestsRun,
        long baselineDurationMillis,
        boolean coverageAvailable,
        long totalDurationMillis,
        MutationScore score,
        List<MutationResult> results) {

    public static final int SCHEMA_VERSION = 1;

    public MutationRunReport {
        sourceDirs = List.copyOf(sourceDirs);
        testDirs = List.copyOf(testDirs);
        operators = List.copyOf(operators);
        results = List.copyOf(results);
    }
}
