package io.gosumutation.gosu;

import gw.lang.parser.IParseTree;
import gw.lang.parser.IParsedElement;

import java.util.List;
import java.util.Optional;

/** Text of a Gosu file plus helpers to map syntax tree elements back to it. */
public final class GosuSource {

    /** A piece of source text and where it is. */
    public record Token(int start, int end, String text) { }

    private final String text;

    public GosuSource(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    /** The exact text of an element, or empty when the parser gives it no location (synthetic code). */
    public Optional<Token> tokenOf(IParsedElement element) {
        IParseTree location = element == null ? null : element.getLocation();
        if (location == null || location.getLength() <= 0) {
            return Optional.empty();
        }
        int start = location.getOffset();
        int end = start + location.getLength();
        if (start < 0 || end > text.length()) {
            return Optional.empty();
        }
        return Optional.of(new Token(start, end, text.substring(start, end)));
    }

    /**
     * Finds the operator of a binary expression: the first of {@code candidates} that appears
     * between its operands, ignoring comments. Word operators ({@code and}, {@code or}) only match
     * whole words. List longer candidates first ({@code >=} before {@code >}).
     */
    public Optional<Token> operatorBetween(IParsedElement leftOperand, IParsedElement rightOperand, List<String> candidates) {
        Optional<Token> left = tokenOf(leftOperand);
        Optional<Token> right = tokenOf(rightOperand);
        if (left.isEmpty() || right.isEmpty() || left.get().end() > right.get().start()) {
            return Optional.empty();
        }
        int end = right.get().start();
        int i = left.get().end();
        while (i < end) {
            if (text.startsWith("//", i)) {
                int newline = text.indexOf('\n', i);
                i = newline < 0 ? end : newline + 1;
            } else if (text.startsWith("/*", i)) {
                int close = text.indexOf("*/", i + 2);
                i = close < 0 ? end : close + 2;
            } else {
                for (String candidate : candidates) {
                    if (matchesAt(candidate, i, end)) {
                        return Optional.of(new Token(i, i + candidate.length(), candidate));
                    }
                }
                i++;
            }
        }
        return Optional.empty();
    }

    private boolean matchesAt(String candidate, int index, int limit) {
        int candidateEnd = index + candidate.length();
        if (candidateEnd > limit || !text.startsWith(candidate, index)) {
            return false;
        }
        if (!Character.isLetter(candidate.charAt(0))) {
            return true;
        }
        boolean wordStart = index == 0 || !Character.isJavaIdentifierPart(text.charAt(index - 1));
        boolean wordEnd = candidateEnd >= text.length() || !Character.isJavaIdentifierPart(text.charAt(candidateEnd));
        return wordStart && wordEnd;
    }

    /** 1-based line of an offset. */
    public int lineOf(int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 1-based column of an offset. */
    public int columnOf(int offset) {
        return offset - text.lastIndexOf('\n', offset - 1);
    }

    /** The whole line that contains {@code offset}, without leading/trailing whitespace. */
    public static String lineAt(String content, int offset) {
        int start = content.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int end = content.indexOf('\n', offset);
        return content.substring(start, end < 0 ? content.length() : end).strip();
    }
}
