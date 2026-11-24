package io.gosumutation.gosu.operators;

import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.IRelationalExpression;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuSource;
import io.gosumutation.gosu.SourceEdit;

import java.util.List;
import java.util.Map;

/** {@code >=} to {@code >}, {@code <=} to {@code <}, {@code >} to {@code >=}, {@code <} to {@code <=}. */
public final class RelationalBoundaryOperator implements GosuMutationOperator {

    private static final List<String> TOKENS = List.of(">=", "<=", ">", "<");
    private static final Map<String, String> REPLACEMENTS = Map.of(">=", ">", "<=", "<", ">", ">=", "<", "<=");

    @Override
    public String id() {
        return "RELATIONAL_BOUNDARY";
    }

    @Override
    public String category() {
        return "Relational";
    }

    @Override
    public String description() {
        return "Moves the boundary of a comparison: >= to >, <= to <, > to >=, < to <=";
    }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (!(element instanceof IRelationalExpression expression)) {
            return List.of();
        }
        return source.operatorBetween(expression.getLHS(), expression.getRHS(), TOKENS)
                .map(token -> List.of(SourceEdit.replaceToken(token, REPLACEMENTS.get(token.text()))))
                .orElse(List.of());
    }
}
