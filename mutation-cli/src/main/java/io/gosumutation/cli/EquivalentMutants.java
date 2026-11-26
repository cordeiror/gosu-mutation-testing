package io.gosumutation.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads the list of mutants the user marked as equivalent. One mutant id per line; everything after
 * the id (and every line starting with {@code #}) is a comment, so tools can note the class and line.
 */
final class EquivalentMutants {

    static final String FILE_NAME = "gosu-mutation-equivalent.txt";

    private EquivalentMutants() { }

    static Set<String> read(Path file) {
        Set<String> ids = new TreeSet<>();
        if (!Files.isRegularFile(file)) {
            return ids;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                ids.add(trimmed.split("[\\s#]+", 2)[0]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        return ids;
    }
}
