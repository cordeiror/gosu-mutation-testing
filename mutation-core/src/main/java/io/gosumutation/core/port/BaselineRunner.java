package io.gosumutation.core.port;

import io.gosumutation.core.MutationConfiguration;

/** Compiles and tests the unmodified project, collecting coverage when possible. */
public interface BaselineRunner {

    BaselineResult run(MutationConfiguration configuration);
}
