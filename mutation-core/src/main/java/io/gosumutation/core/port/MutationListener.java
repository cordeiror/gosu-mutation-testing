package io.gosumutation.core.port;

import io.gosumutation.core.MutationDescriptor;
import io.gosumutation.core.MutationResult;
import io.gosumutation.core.MutationRunReport;

import java.util.List;

/** Progress callbacks. {@link #mutantFinished} may be called from several threads. */
public interface MutationListener {

    MutationListener NONE = new MutationListener() { };

    default void baselineStarted() { }

    default void baselineFinished(BaselineResult baseline) { }

    default void mutantsGenerated(List<MutationDescriptor> mutants) { }

    default void mutantFinished(MutationResult result) { }

    default void runFinished(MutationRunReport report) { }
}
