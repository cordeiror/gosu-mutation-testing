package io.gosumutation.core;

import java.util.Collection;

/**
 * Aggregated counts and scores for a set of mutants.
 *
 * <p><b>Mutation score</b> = {@code killed / (killed + survived) * 100}. Only mutants that compiled
 * and were run to completion against the tests are in the denominator. Mutants without coverage,
 * timeouts, compilation errors, run errors, mutants that were not run and mutants marked as
 * equivalent are reported separately and never counted as killed.
 *
 * <p><b>Score including uncovered</b> = {@code killed / (killed + survived + noCoverage) * 100}.
 * It additionally penalizes code that no test executes.
 *
 * <p>Both scores are {@code 0} when their denominator is zero.
 */
public record MutationScore(
        int total,
        int killed,
        int survived,
        int noCoverage,
        int timedOut,
        int compilationErrors,
        int runErrors,
        int notRun,
        int equivalent,
        double mutationScore,
        double scoreIncludingUncovered) {

    public static MutationScore of(Collection<MutationResult> results) {
        int killed = 0, survived = 0, noCoverage = 0, timedOut = 0, compilationErrors = 0, runErrors = 0, notRun = 0, equivalent = 0;
        for (MutationResult result : results) {
            switch (result.status()) {
                case KILLED -> killed++;
                case SURVIVED -> survived++;
                case NO_COVERAGE -> noCoverage++;
                case TIMED_OUT -> timedOut++;
                case COMPILATION_ERROR -> compilationErrors++;
                case RUN_ERROR -> runErrors++;
                case NOT_RUN -> notRun++;
                case EQUIVALENT -> equivalent++;
            }
        }
        return new MutationScore(results.size(), killed, survived, noCoverage, timedOut, compilationErrors,
                runErrors, notRun, equivalent,
                percentage(killed, killed + survived),
                percentage(killed, killed + survived + noCoverage));
    }

    private static double percentage(int part, int whole) {
        if (whole == 0) {
            return 0.0;
        }
        return Math.round(part * 1000.0 / whole) / 10.0;
    }
}
