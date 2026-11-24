package io.gosumutation.core;

/** Final classification of a single mutant. See docs/architecture.md for the scoring rules. */
public enum MutationStatus {
    /** At least one test failed because of the mutation. */
    KILLED,
    /** The mutant compiled and every test still passed. */
    SURVIVED,
    /** The baseline test run never executed the mutated line, so the mutant was not run. */
    NO_COVERAGE,
    /** Compilation or test execution exceeded the configured timeout and was terminated. */
    TIMED_OUT,
    /** The mutated source did not compile (an invalid mutant). */
    COMPILATION_ERROR,
    /** Infrastructure failure: the mutant could not be evaluated. */
    RUN_ERROR,
    /** The run was interrupted before this mutant was evaluated. */
    NOT_RUN;

    /** Only mutants that were really evaluated against the tests count towards the mutation score. */
    public boolean countsTowardsScore() {
        return this == KILLED || this == SURVIVED;
    }
}
