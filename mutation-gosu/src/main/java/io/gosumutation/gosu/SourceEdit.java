package io.gosumutation.gosu;

/**
 * Replace {@code [start, end)} of the source with {@code replacement}.
 *
 * @param description human readable summary, e.g. {@code replaced >= with >}
 */
public record SourceEdit(int start, int end, String replacement, String description) {

    public static SourceEdit replaceToken(GosuSource.Token token, String replacement) {
        return new SourceEdit(token.start(), token.end(), replacement,
                "replaced " + token.text() + " with " + replacement);
    }
}
