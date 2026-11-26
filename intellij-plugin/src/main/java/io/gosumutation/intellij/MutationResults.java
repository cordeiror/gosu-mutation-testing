package io.gosumutation.intellij;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The results of the last run of one class, read from mutation-report.json, plus the mutants the
 * user marked as equivalent since then (they show as {@code EQUIVALENT} right away).
 */
record MutationResults(Path reportFile, String generatedAt, List<Mutant> mutants) {

    static final String KILLED = "KILLED";
    static final String SURVIVED = "SURVIVED";
    static final String NO_COVERAGE = "NO_COVERAGE";
    static final String EQUIVALENT = "EQUIVALENT";
    /** A mutant the report lists as equivalent but that the user unmarked: its real status needs a new run. */
    static final String RUN_AGAIN = "RUN_AGAIN";

    /** One mutant as the IDE shows it. {@code reportStatus} is what the run found. */
    record Mutant(String id, String status, String reportStatus, String operator, String className, String method,
                  String relativePath, int line, int column, String description, String originalLine,
                  String mutatedLine, List<String> killingTests, String details) {

        /** Mutants the tests do not detect yet: the ones worth a closer look. */
        boolean undetected() {
            return SURVIVED.equals(status) || NO_COVERAGE.equals(status);
        }
    }

    MutationResults {
        mutants = List.copyOf(mutants);
    }

    static MutationResults read(Path reportFile) throws IOException {
        Object root = Json.parse(Files.readString(reportFile, StandardCharsets.UTF_8));
        if (!(root instanceof Map<?, ?> report) || !(report.get("results") instanceof List<?> results)) {
            throw new IOException("Not a gosu-mutation report: " + reportFile);
        }
        List<Mutant> mutants = results.stream().map(r -> mutant((Map<?, ?>) r)).toList();
        return new MutationResults(reportFile, text(report, "generatedAt"), mutants);
    }

    private static Mutant mutant(Map<?, ?> result) {
        Map<?, ?> m = (Map<?, ?>) result.get("mutation");
        String status = text(result, "status");
        List<String> killing = result.get("killingTests") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        return new Mutant(text(m, "id"), status, status, text(m, "operatorId"), text(m, "className"), text(m, "methodName"),
                text(m, "relativePath"), number(m, "line"), number(m, "column"), text(m, "description"),
                text(m, "originalLine"), text(m, "mutatedLine"), killing, text(result, "details"));
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }

    private static int number(Map<?, ?> map, String key) {
        return map.get(key) instanceof Double d ? d.intValue() : 0;
    }

    /** The same results with {@code equivalentIds} applied, as the next run will see them. */
    MutationResults withEquivalent(Set<String> equivalentIds) {
        return new MutationResults(reportFile, generatedAt, mutants.stream().map(m -> {
            String status;
            if (equivalentIds.contains(m.id())) {
                status = EQUIVALENT;
            } else if (EQUIVALENT.equals(m.reportStatus())) {
                status = RUN_AGAIN;
            } else {
                status = m.reportStatus();
            }
            return new Mutant(m.id(), status, m.reportStatus(), m.operator(), m.className(), m.method(), m.relativePath(),
                    m.line(), m.column(), m.description(), m.originalLine(), m.mutatedLine(), m.killingTests(), m.details());
        }).toList());
    }

    long count(String status) {
        return mutants.stream().filter(m -> m.status().equals(status)).count();
    }

    /** killed / (killed + survived), as in the report; null when nothing was evaluated. */
    Double score() {
        long killed = count(KILLED);
        long survived = count(SURVIVED);
        return killed + survived == 0 ? null : Math.round(killed * 1000.0 / (killed + survived)) / 10.0;
    }

    List<Mutant> undetected() {
        return mutants.stream().filter(Mutant::undetected).toList();
    }

    /** "75.0% · 3 killed · 1 survived · 4 mutants". */
    String summary() {
        Double score = score();
        StringBuilder text = new StringBuilder(score == null ? "score n/a" : score + "%");
        text.append(" · ").append(count(KILLED)).append(" killed · ").append(count(SURVIVED)).append(" survived");
        for (String other : List.of(NO_COVERAGE, EQUIVALENT, "TIMED_OUT", "COMPILATION_ERROR", "RUN_ERROR", RUN_AGAIN)) {
            long n = count(other);
            if (n > 0) {
                text.append(" · ").append(n).append(' ').append(label(other).toLowerCase());
            }
        }
        return text.append(" · ").append(mutants.size()).append(" mutants").toString();
    }

    static String label(String status) {
        return switch (status) {
            case KILLED -> "Killed";
            case SURVIVED -> "Survived";
            case NO_COVERAGE -> "No coverage";
            case EQUIVALENT -> "Equivalent";
            case RUN_AGAIN -> "Run again";
            case "TIMED_OUT" -> "Timed out";
            case "COMPILATION_ERROR" -> "Compilation error";
            case "RUN_ERROR" -> "Run error";
            case "NOT_RUN" -> "Not run";
            default -> status;
        };
    }
}
