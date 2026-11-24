package io.gosumutation.gosu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/** Registry of the Gosu mutation operators available on the class path. */
public final class GosuOperators {

    private GosuOperators() { }

    /** Built-in operators plus any contributed through {@link ServiceLoader}, sorted by id. */
    public static List<GosuMutationOperator> available() {
        List<GosuMutationOperator> operators = new ArrayList<>();
        ServiceLoader.load(GosuMutationOperator.class, GosuOperators.class.getClassLoader()).forEach(operators::add);
        operators.sort(Comparator.comparing(GosuMutationOperator::id));
        return operators;
    }
}
