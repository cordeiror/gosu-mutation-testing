package io.gosumutation.gosu.operators;

import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.IConditionalAndExpression;
import gw.lang.parser.expressions.IConditionalOrExpression;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuSource;
import io.gosumutation.gosu.SourceEdit;

import java.util.List;
import java.util.Map;

/** {@code and} to {@code or} and {@code or} to {@code and}, in both spellings ({@code &&}, {@code ||}). */
public final class LogicalOperator implements GosuMutationOperator {

    private static final Map<String, String> REPLACEMENTS = Map.of("and", "or", "&&", "||", "or", "and", "||", "&&");

    @Override
    public String id() {
        return "LOGICAL";
    }

    @Override
    public String category() {
        return "Logical";
    }

    @Override
    public String description() {
        return "Swaps logical connectives: and to or, or to and";
    }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (element instanceof IConditionalAndExpression and) {
            return swap(source, and.getLHS(), and.getRHS(), List.of("and", "&&"));
        }
        if (element instanceof IConditionalOrExpression or) {
            return swap(source, or.getLHS(), or.getRHS(), List.of("or", "||"));
        }
        return List.of();
    }

    private static List<SourceEdit> swap(GosuSource source, IParsedElement left, IParsedElement right, List<String> tokens) {
        return source.operatorBetween(left, right, tokens)
                .map(token -> List.of(SourceEdit.replaceToken(token, REPLACEMENTS.get(token.text()))))
                .orElse(List.of());
    }
}
