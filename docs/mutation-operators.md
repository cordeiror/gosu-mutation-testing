# Mutation operators

List them from the command line with `gosu-mutation operators`. Select a subset with
`--operators ID,ID`.

| Id | Category | Original | Mutant |
| --- | --- | --- | --- |
| `RELATIONAL_BOUNDARY` | Relational | `a >= b` | `a > b` |
| | | `a <= b` | `a < b` |
| | | `a > b` | `a >= b` |
| | | `a < b` | `a <= b` |
| `EQUALITY` | Relational | `a == b` | `a != b` |
| | | `a != b`, `a <> b` | `a == b` |
| `LOGICAL` | Logical | `a and b`, `a && b` | `a or b`, `a \|\| b` |
| | | `a or b`, `a \|\| b` | `a and b`, `a && b` |
| `BOOLEAN_LITERAL` | Boolean | `true` | `false` |
| | | `false` | `true` |
| `NUMERIC_LITERAL` | Numeric | `10` | `11` |
| | | `1.5` | `2.5` |
| `BOOLEAN_RETURN` | Boolean return | `return expression` | `return true` and `return false` (two mutants) |

`<` to `<=` and `!=` to `==` were added to the list in the original brief because they are the
natural counterparts of the requested ones.

## Rules common to all operators

- Mutation points come from the Gosu syntax tree. Text in comments and in string literals is never
  mutated. Code inside a string template expression (`"${a >= b}"`) is real code and is mutated.
- Code inside annotations is skipped.
- Synthetic elements (code the compiler adds) are skipped.
- An edit is discarded if the text on disk does not match what the parser saw at that offset.
- Each mutant changes exactly one place.

## Notes per operator

- **RELATIONAL_BOUNDARY** applies to `IRelationalExpression`. The operator token is searched only
  between the left and right operand, skipping comments.
- **EQUALITY** applies to `IEqualityExpression`. Identity comparisons (`===`, `!==`) are left alone.
- **LOGICAL** applies to `IConditionalAndExpression` and `IConditionalOrExpression`, keeping the
  spelling used in the source (`and` becomes `or`, `&&` becomes `||`).
- **BOOLEAN_LITERAL** applies to `IBooleanLiteralExpression`.
- **NUMERIC_LITERAL** applies to `INumericLiteralExpression` written as plain decimal digits, with
  or without a fraction. Literals with a type suffix, an exponent, or in hexadecimal or binary
  notation are skipped. Incrementing the largest `int` produces a mutant that does not compile; it
  is reported as `COMPILATION_ERROR` and does not affect the score.
- **BOOLEAN_RETURN** applies to `IReturnStatement` whose value has type `boolean` or
  `java.lang.Boolean` and is not already a literal (those belong to `BOOLEAN_LITERAL`). One of the
  two mutants is frequently equivalent to the original for a given test, which is expected.

## Adding an operator

No change to the engine is needed.

1. Implement `io.gosumutation.gosu.GosuMutationOperator`:

```java
public final class NegateConditionOperator implements GosuMutationOperator {

    @Override public String id() { return "NEGATE_CONDITION"; }
    @Override public String category() { return "Logical"; }
    @Override public String description() { return "Wraps an if condition in not (...)"; }

    @Override
    public List<SourceEdit> mutate(IParsedElement element, GosuSource source) {
        if (!(element instanceof IIfStatement statement)) {
            return List.of();
        }
        return source.tokenOf(statement.getExpression())
                .map(token -> List.of(new SourceEdit(token.start(), token.end(),
                        "not (" + token.text() + ")", "negated condition")))
                .orElse(List.of());
    }
}
```

   `mutate` is called once for every element of the syntax tree. Return an empty list when the
   operator does not apply. `GosuSource.tokenOf(element)` gives the exact text range of an element;
   `GosuSource.operatorBetween(lhs, rhs, candidates)` finds a binary operator token.

2. Register the class name in a file named
   `META-INF/services/io.gosumutation.gosu.GosuMutationOperator`. For a built-in operator that file
   is in `mutation-gosu/src/main/resources`. An operator in a separate jar brings its own file and
   only needs to be on the class path of the tool.

3. Add cases to `GosuMutationGeneratorTest` (it runs the real parser) and a row to this document.

The example above is illustrative and is not part of the project.
