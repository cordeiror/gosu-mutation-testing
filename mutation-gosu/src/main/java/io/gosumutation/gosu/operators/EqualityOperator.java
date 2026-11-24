package io.gosumutation.gosu.operators;

import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.IEqualityExpression;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuSource;
import io.gosumutation.gosu.SourceEdit;

import java.util.List;
import java.util.Map;

/** {@code ==} to {@code !=} and {@code !=} (or {@code <>}) to {@code ==}. */
public final class EqualityOperator implements GosuMutationOperator {

    // "===" and "!==" are listed so that identity comparisons are recognized and left alone.
    private static final List<String> TOKENS = List.of("===", "!==", "==", "!=", "<>");
    private static final Map<String, String> REPLACEMENTS = Map.of("==", "!=", "!=", "==", "<>", "==");

    @Override
    public String id() {
        return "EQUALITY";
    }

    @Override
    public String category() {
        return "Relational";
    }

    @Override
    public String description() {
        return "Negates an equality check: == to !=, != to ==";
    }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (!(element instanceof IEqualityExpression expression)) {
            return List.of();
        }
        return source.operatorBetween(expression.getLHS(), expression.getRHS(), TOKENS)
                .filter(token -> REPLACEMENTS.containsKey(token.text()))
                .map(token -> List.of(SourceEdit.replaceToken(token, REPLACEMENTS.get(token.text()))))
                .orElse(List.of());
    }
}
