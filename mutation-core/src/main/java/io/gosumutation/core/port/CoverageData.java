package io.gosumutation.core.port;

/** Line coverage produced by the baseline test run. */
public interface CoverageData {

    /** Used when coverage could not be collected: every mutant is then executed. */
    CoverageData UNAVAILABLE = new CoverageData() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean isCovered(String relativeSourcePath, int line) {
            return true;
        }
    };

    boolean available();

    /**
     * @param relativeSourcePath source file relative to its source root, with '/' separators
     * @return {@code false} only when the line is known to hold code that no test executed
     */
    boolean isCovered(String relativeSourcePath, int line);
}
