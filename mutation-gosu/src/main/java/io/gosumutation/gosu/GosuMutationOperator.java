package io.gosumutation.gosu;

import gw.lang.parser.IParsedElement;
import io.gosumutation.core.MutationOperator;

import java.util.List;

/**
 * A mutation operator for Gosu. Implementations are discovered with {@link java.util.ServiceLoader}:
 * to add an operator, implement this interface and list the class in
 * {@code META-INF/services/io.gosumutation.gosu.GosuMutationOperator}. The engine is not modified.
 */
public interface GosuMutationOperator extends MutationOperator {

    /**
     * Called once for every element of the syntax tree.
     *
     * @return the edits this operator wants to make to {@code element}; empty when it does not apply
     */
    List<SourceEdit> mutate(IParsedElement element, GosuSource source);
}
