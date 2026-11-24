package io.gosumutation.gosu.operators;

import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.IBooleanLiteralExpression;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuSource;
import io.gosumutation.gosu.SourceEdit;

import java.util.List;

/** {@code true} to {@code false} and {@code false} to {@code true}. */
public final class BooleanLiteralOperator implements GosuMutationOperator {

    @Override
    public String id() {
        return "BOOLEAN_LITERAL";
    }

    @Override
    public String category() {
        return "Boolean";
    }

    @Override
    public String description() {
        return "Inverts boolean literals: true to false, false to true";
    }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (!(element instanceof IBooleanLiteralExpression)) {
            return List.of();
        }
        return source.tokenOf(element)
                .filter(token -> token.text().equals("true") || token.text().equals("false"))
                .map(token -> List.of(SourceEdit.replaceToken(token, token.text().equals("true") ? "false" : "true")))
                .orElse(List.of());
    }
}
