package io.gosumutation.core.port;

import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.MutationDescriptor;

import java.util.List;

/** Analyzes the production sources and lists every mutation that the enabled operators can make. */
public interface MutationGenerator {

    List<MutationDescriptor> generate(MutationConfiguration configuration);
}
