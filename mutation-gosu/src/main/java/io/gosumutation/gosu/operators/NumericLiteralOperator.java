package io.gosumutation.gosu.operators;

import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.INumericLiteralExpression;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuSource;
import io.gosumutation.gosu.SourceEdit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Adds one to a numeric literal: {@code 10} to {@code 11}, {@code 1.5} to {@code 2.5}.
 *
 * <p>Only plain decimal literals are mutated. Literals with a type suffix, an exponent or in
 * hexadecimal/binary notation are skipped rather than risk producing invalid source.
 */
public final class NumericLiteralOperator implements GosuMutationOperator {

    private static final Pattern INTEGER = Pattern.compile("\\d+");
    private static final Pattern DECIMAL = Pattern.compile("\\d+\\.\\d+");

    @Override
    public String id() {
        return "NUMERIC_LITERAL";
    }

    @Override
    public String category() {
        return "Numeric";
    }

    @Override
    public String description() {
        return "Increments numeric literals by one: 10 to 11";
    }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (!(element instanceof INumericLiteralExpression)) {
            return List.of();
        }
        return source.tokenOf(element).map(token -> {
            String text = token.text();
            if (INTEGER.matcher(text).matches()) {
                return List.of(SourceEdit.replaceToken(token, new BigInteger(text).add(BigInteger.ONE).toString()));
            }
            if (DECIMAL.matcher(text).matches()) {
                return List.of(SourceEdit.replaceToken(token, new BigDecimal(text).add(BigDecimal.ONE).toPlainString()));
            }
            return List.<SourceEdit>of();
        }).orElse(List.of());
    }
}
