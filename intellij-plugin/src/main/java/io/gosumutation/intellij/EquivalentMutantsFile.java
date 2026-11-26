package io.gosumutation.intellij;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The trial's {@code gosu-mutation-equivalent.txt}: ids of mutants the user marked as equivalent.
 * gosu-mutation reads it on every run of that trial; text after the id is a comment for people.
 */
final class EquivalentMutantsFile {

    static final String FILE_NAME = "gosu-mutation-equivalent.txt";
    private static final String HEADER = "# Mutants marked as equivalent: no test can tell them from the original code.\n"
            + "# One id per line; the rest of the line is a comment. Ids change when the class is edited.\n";

    private EquivalentMutantsFile() { }

    static Path of(Path trialDir) {
        return trialDir.resolve(FILE_NAME);
    }

    static Set<String> read(Path file) {
        Set<String> ids = new TreeSet<>();
        for (String line : lines(file)) {
            String id = idOf(line);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    static void add(Path file, MutationResults.Mutant mutant) throws IOException {
        if (read(file).contains(mutant.id())) {
            return;
        }
        List<String> lines = new ArrayList<>(lines(file));
        if (lines.isEmpty()) {
            lines.addAll(HEADER.lines().toList());
        }
        lines.add(mutant.id() + "  # " + mutant.className() + " line " + mutant.line() + ": " + mutant.description());
        write(file, lines);
    }

    static void remove(Path file, String id) throws IOException {
        List<String> lines = lines(file);
        List<String> kept = lines.stream().filter(line -> !id.equals(idOf(line))).toList();
        if (kept.size() != lines.size()) {
            write(file, kept);
        }
    }

    private static String idOf(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        return trimmed.split("[\\s#]+", 2)[0];
    }

    private static List<String> lines(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void write(Path file, List<String> lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }
}
