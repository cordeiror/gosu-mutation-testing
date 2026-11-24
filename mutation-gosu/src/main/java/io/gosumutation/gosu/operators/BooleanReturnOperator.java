package io.gosumutation.gosu.operators;

import gw.lang.parser.IExpression;
import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.IBooleanLiteralExpression;
import gw.lang.parser.statements.IReturnStatement;
import gw.lang.reflect.IType;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuSource;
import io.gosumutation.gosu.SourceEdit;

import java.util.List;
import java.util.Set;

/**
 * Replaces the value of a boolean {@code return} with {@code true} and with {@code false}.
 *
 * <p>Returns that already are a literal are left to {@link BooleanLiteralOperator}.
 */
public final class BooleanReturnOperator implements GosuMutationOperator {

    private static final Set<String> BOOLEAN_TYPES = Set.of("boolean", "java.lang.Boolean");

    @Override
    public String id() {
        return "BOOLEAN_RETURN";
    }

    @Override
    public String category() {
        return "Boolean return";
    }

    @Override
    public String description() {
        return "Replaces a returned boolean expression with true and with false";
    }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (!(element instanceof IReturnStatement statement)) {
            return List.of();
        }
        IExpression value = statement.getValue();
        if (value == null || value instanceof IBooleanLiteralExpression) {
            return List.of();
        }
        IType type = value.getType();
        if (type == null || !BOOLEAN_TYPES.contains(type.getName())) {
            return List.of();
        }
        return source.tokenOf(value)
                .map(token -> List.of(
                        new SourceEdit(token.start(), token.end(), "true", "replaced boolean return value with true"),
                        new SourceEdit(token.start(), token.end(), "false", "replaced boolean return value with false")))
                .orElse(List.of());
    }
}
