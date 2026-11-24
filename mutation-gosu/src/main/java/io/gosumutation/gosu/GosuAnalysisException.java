package io.gosumutation.gosu;

/** A Gosu source file could not be loaded or parsed. */
public class GosuAnalysisException extends RuntimeException {

    public GosuAnalysisException(String message) {
        super(message);
    }

    public GosuAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
