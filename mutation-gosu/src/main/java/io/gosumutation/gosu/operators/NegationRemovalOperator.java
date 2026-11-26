package io.gosumutation.gosu.operators;

import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.IUnaryNotPlusMinusExpression;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuSource;
import io.gosumutation.gosu.SourceEdit;

import java.util.List;

/** Removes a logical negation: {@code not x} and {@code !x} become {@code x}. */
public final class NegationRemovalOperator implements GosuMutationOperator {

    @Override
    public String id() {
        return "NEGATION_REMOVAL";
    }

    @Override
    public String category() {
        return "Logical";
    }

    @Override
    public String description() {
        return "Removes a logical negation: not x and !x become x";
    }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (!(element instanceof IUnaryNotPlusMinusExpression negation) || !negation.isNot()) {
            return List.of();
        }
        return source.tokenOf(negation)
                .flatMap(whole -> source.tokenOf(negation.getExpression()).map(operand -> {
                    String text = whole.text();
                    String operator = text.startsWith("!") ? "!" : text.startsWith("not") ? "not" : null;
                    if (operator == null || operand.start() <= whole.start() || operand.end() > whole.end()) {
                        return List.<SourceEdit>of();
                    }
                    // Keep everything after the operator (a closing parenthesis outside the operand included).
                    String replacement = text.substring(operator.length()).stripLeading();
                    return List.of(new SourceEdit(whole.start(), whole.end(), replacement, "removed " + operator));
                }))
                .orElse(List.of());
    }
}
