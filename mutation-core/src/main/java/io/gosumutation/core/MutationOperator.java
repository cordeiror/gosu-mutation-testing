package io.gosumutation.core;

/**
 * Language independent description of a mutation operator.
 *
 * <p>Language modules extend this contract with the means to locate mutation points in their own
 * syntax tree (see {@code GosuMutationOperator} in mutation-gosu). The engine only needs the
 * identity of an operator to enable, disable and report on it.
 */
public interface MutationOperator {

    /** Stable, upper snake case identifier used in configuration and reports, e.g. {@code RELATIONAL_BOUNDARY}. */
    String id();

    /** Category shown in reports, e.g. {@code Relational}. */
    String category();

    /** One line, human readable description. */
    String description();
}
