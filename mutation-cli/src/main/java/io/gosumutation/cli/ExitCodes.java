package io.gosumutation.cli;

/** Process exit codes of the command line tool. */
final class ExitCodes {

    static final int OK = 0;
    /** The baseline failed or the run could not be completed. */
    static final int FAILURE = 1;
    /** Invalid command line (picocli's default for usage errors). */
    static final int USAGE = 2;
    /** The run completed but the mutation score is below {@code --threshold}. */
    static final int BELOW_THRESHOLD = 3;

    private ExitCodes() { }
}
