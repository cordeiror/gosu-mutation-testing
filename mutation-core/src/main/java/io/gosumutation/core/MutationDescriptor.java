package io.gosumutation.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One concrete mutation: replace {@code [startOffset, endOffset)} of a source file with
 * {@code replacementText}.
 *
 * @param id              deterministic unique id (see {@link #computeId})
 * @param operatorId      id of the {@link MutationOperator} that produced it
 * @param sourceRoot      absolute path of the source directory that contains the file
 * @param relativePath    path of the file inside {@code sourceRoot}, always with '/' separators
 * @param className       fully qualified name of the mutated type
 * @param methodName      enclosing function/property, or {@code <class>} for class level code
 * @param line            1-based line of the mutation
 * @param column          1-based column of the mutation
 * @param startOffset     0-based char offset (inclusive) in the file content
 * @param endOffset       0-based char offset (exclusive) in the file content
 * @param originalText    text being replaced, used to validate the file before mutating it
 * @param replacementText text written in its place
 * @param description     human readable summary, e.g. {@code replaced >= with >}
 * @param originalLine    the source line before the mutation (trimmed)
 * @param mutatedLine     the same line after the mutation (trimmed)
 */
public record MutationDescriptor(
        String id,
        String operatorId,
        String sourceRoot,
        String relativePath,
        String className,
        String methodName,
        int line,
        int column,
        int startOffset,
        int endOffset,
        String originalText,
        String replacementText,
        String description,
        String originalLine,
        String mutatedLine) {

    public MutationDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(originalText, "originalText");
        Objects.requireNonNull(replacementText, "replacementText");
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("Invalid offsets " + startOffset + ".." + endOffset);
        }
    }

    /**
     * Builds the mutant id. It depends only on the location and content of the mutation, so the
     * same source always yields the same ids, across runs and machines.
     */
    public static String computeId(String relativePath, String operatorId, int startOffset,
                                   String originalText, String replacementText) {
        String key = relativePath + '|' + operatorId + '|' + startOffset + '|' + originalText + '|' + replacementText;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /**
     * Returns {@code source} with this mutation applied.
     *
     * @throws IllegalStateException when the source does not contain the expected original text at
     *                               the recorded position (the file changed since it was analyzed)
     */
    public String applyTo(String source) {
        if (endOffset > source.length() || !source.substring(startOffset, endOffset).equals(originalText)) {
            throw new IllegalStateException("Source of " + relativePath + " does not match mutation " + id
                    + ": expected '" + originalText + "' at offset " + startOffset);
        }
        return source.substring(0, startOffset) + replacementText + source.substring(endOffset);
    }
}
