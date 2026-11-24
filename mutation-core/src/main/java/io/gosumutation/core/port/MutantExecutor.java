package io.gosumutation.core.port;

import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.MutationDescriptor;
import io.gosumutation.core.MutationResult;

/**
 * Evaluates one mutant in isolation. Implementations must never touch the original project files
 * and must be safe to call from several threads at once.
 */
public interface MutantExecutor {

    MutationResult execute(MutationDescriptor mutant, MutationConfiguration configuration);
}
