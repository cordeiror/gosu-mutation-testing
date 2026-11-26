package io.gosumutation.gosu.operators;

import gw.lang.parser.IExpression;
import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.IStringLiteralExpression;
import gw.lang.parser.statements.IReturnStatement;
import gw.lang.reflect.IType;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuSource;
import io.gosumutation.gosu.SourceEdit;

import java.util.List;

/**
 * Replaces the value of a {@code String} {@code return} with {@code ""}. A return of {@code ""}
 * becomes {@code "mutated"}, so tests that expect an empty result are checked too.
 */
public final class StringReturnOperator implements GosuMutationOperator {

    static final String NON_EMPTY = "\"mutated\"";

    @Override
    public String id() {
        return "STRING_RETURN";
    }

    @Override
    public String category() {
        return "Return value";
    }

    @Override
    public String description() {
        return "Replaces a returned String with \"\" (and \"\" with \"mutated\")";
    }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (!(element instanceof IReturnStatement statement)) {
            return List.of();
        }
        IExpression value = statement.getValue();
        if (value == null) {
            return List.of();
        }
        IType type = value.getType();
        if (type == null || !"java.lang.String".equals(type.getName())) {
            return List.of();
        }
        return source.tokenOf(value)
                .map(token -> {
                    boolean empty = value instanceof IStringLiteralExpression literal && literal.getValue().isEmpty()
                            && token.text().equals("\"\"");
                    if (token.text().equals("null")) {
                        return List.<SourceEdit>of();
                    }
                    String replacement = empty ? NON_EMPTY : "\"\"";
                    return List.of(new SourceEdit(token.start(), token.end(), replacement,
                            "replaced String return value with " + replacement));
                })
                .orElse(List.of());
    }
}
