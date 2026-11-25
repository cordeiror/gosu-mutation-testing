package io.gosumutation.intellij;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds the test of a Gosu class from folder mappings such as {@code src/main/gosu=src/test/gosu}:
 * {@code .../src/main/gosu/a/b/Foo.gs} becomes {@code .../src/test/gosu/a/b/FooTest.gs}.
 */
final class TestFileLocator {

    private TestFileLocator() { }

    /** @param mappings entries {@code from=to}, separated by ';' or new lines */
    static Optional<Path> find(Path source, String mappings) {
        return candidates(source, mappings).stream().filter(Files::isRegularFile).findFirst();
    }

    static List<Path> candidates(Path source, String mappings) {
        String fileName = source.getFileName().toString();
        if (!fileName.endsWith(".gs")) {
            return List.of();
        }
        String testName = fileName.substring(0, fileName.length() - 3) + "Test.gs";
        String path = source.toAbsolutePath().normalize().toString().replace('\\', '/');
        List<Path> candidates = new ArrayList<>();
        for (String mapping : parse(mappings)) {
            String[] parts = mapping.split("=", 2);
            String from = "/" + trimSlashes(parts[0]) + "/";
            String to = "/" + trimSlashes(parts[1]) + "/";
            int index = path.lastIndexOf(from);
            if (index >= 0) {
                String mapped = path.substring(0, index) + to + path.substring(index + from.length());
                candidates.add(Path.of(mapped).resolveSibling(testName));
            }
        }
        candidates.add(source.resolveSibling(testName));
        return candidates;
    }

    /** Folder where the chooser should start: the mapped test folder, or its nearest existing parent. */
    static Path suggestedFolder(Path source, String mappings) {
        for (Path candidate : candidates(source, mappings)) {
            Path folder = candidate.getParent();
            while (folder != null && !Files.isDirectory(folder)) {
                folder = folder.getParent();
            }
            if (folder != null && !folder.equals(folder.getRoot())) {
                return folder;
            }
        }
        return source.getParent();
    }

    static List<String> parse(String mappings) {
        List<String> result = new ArrayList<>();
        if (mappings == null) {
            return result;
        }
        for (String entry : mappings.split("[;\\r\\n]+")) {
            String trimmed = entry.trim();
            int equals = trimmed.indexOf('=');
            if (equals > 0 && equals < trimmed.length() - 1) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String trimSlashes(String segment) {
        return segment.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
    }
}
