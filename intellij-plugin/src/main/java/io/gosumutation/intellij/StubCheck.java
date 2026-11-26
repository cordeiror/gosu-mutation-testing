package io.gosumutation.intellij;

import java.util.Collection;
import java.util.List;

/**
 * Outcome of checking whether a class and its tests compile and pass in the trial project, read
 * from what gosu-mutation left behind: the exit code, baseline-failure.txt and the console output.
 */
record StubCheck(Kind kind, int errorCount, BaselineErrors.Missing missing, String message) {

    enum Kind {
        /** Compiles and every test passes: ready for mutation testing. */
        READY,
        /** The class or its tests do not compile: stubs are missing or incomplete. */
        DOES_NOT_COMPILE,
        /** Compiles, but tests fail: a stub probably behaves differently from the real type. */
        TESTS_FAIL,
        /** The tool stopped before compiling (for example no test found). */
        OTHER
    }

    static final int LOG_TAIL_LINES = 15;

    static StubCheck of(int exitCode, String failure, String output, Collection<String> sources) {
        if (exitCode == 0) {
            return new StubCheck(Kind.READY, 0, new BaselineErrors.Missing(List.of(), List.of()), firstLine(output));
        }
        if (BaselineErrors.isCompilationFailure(failure)) {
            return new StubCheck(Kind.DOES_NOT_COMPILE, BaselineErrors.errorCount(failure),
                    BaselineErrors.missing(failure, sources), "");
        }
        BaselineErrors.Missing none = new BaselineErrors.Missing(List.of(), List.of());
        if (failure != null) {
            return new StubCheck(Kind.TESTS_FAIL, 0, none, failure.strip());
        }
        return new StubCheck(Kind.OTHER, 0, none, tail(output));
    }

    private static String firstLine(String output) {
        return output == null ? "" : output.lines().filter(l -> l.startsWith("Baseline OK")).findFirst().orElse("");
    }

    static String tail(String output) {
        if (output == null) {
            return "";
        }
        List<String> lines = output.strip().lines().filter(line -> !line.isBlank()).toList();
        return String.join("\n", lines.subList(Math.max(0, lines.size() - LOG_TAIL_LINES), lines.size()));
    }
}
