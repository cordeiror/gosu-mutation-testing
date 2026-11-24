package io.gosumutation.core;

import io.gosumutation.core.port.BaselineResult;

/** The unmodified project does not compile or its tests do not pass, so mutation testing is meaningless. */
public class BaselineFailedException extends RuntimeException {

    private final transient BaselineResult baseline;

    public BaselineFailedException(BaselineResult baseline) {
        super("Baseline failed: " + baseline.message());
        this.baseline = baseline;
    }

    public BaselineResult baseline() {
        return baseline;
    }
}
