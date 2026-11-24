package io.gosumutation.core;

import java.util.List;

/**
 * Outcome of evaluating one mutant.
 *
 * @param mutation       the mutant
 * @param status         its classification
 * @param killingTests   tests that failed against the mutant (empty unless {@code KILLED})
 * @param durationMillis wall clock time spent compiling and testing the mutant
 * @param details        compiler output, error message or other diagnostic text; may be empty
 */
public record MutationResult(
        MutationDescriptor mutation,
        MutationStatus status,
        List<String> killingTests,
        long durationMillis,
        String details) {

    public MutationResult {
        killingTests = killingTests == null ? List.of() : List.copyOf(killingTests);
        details = details == null ? "" : details;
    }

    public static MutationResult of(MutationDescriptor mutation, MutationStatus status, long durationMillis, String details) {
        return new MutationResult(mutation, status, List.of(), durationMillis, details);
    }
}
