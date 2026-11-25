package io.gosumutation.intellij;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Which tests to use for a class: the files and folders the user chose for it earlier, otherwise
 * the test found through the folder mappings. Empty means "ask".
 */
final class TestSelection {

    private static final String SEPARATOR = "|";

    private TestSelection() { }

    static List<Path> resolve(Path source, Map<String, String> chosen, String mappings) {
        List<Path> remembered = decode(chosen.get(key(source)));
        if (!remembered.isEmpty() && remembered.stream().allMatch(Files::exists)) {
            return remembered;
        }
        return TestFileLocator.find(source, mappings).map(List::of).orElse(List.of());
    }

    static void remember(Map<String, String> chosen, Path source, List<Path> tests) {
        if (tests.isEmpty()) {
            chosen.remove(key(source));
        } else {
            chosen.put(key(source), tests.stream().map(Path::toString).collect(Collectors.joining(SEPARATOR)));
        }
    }

    static List<Path> remembered(Map<String, String> chosen, Path source) {
        return decode(chosen.get(key(source)));
    }

    static String key(Path source) {
        return source.toAbsolutePath().normalize().toString();
    }

    static List<Path> decode(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\" + SEPARATOR)).map(String::trim).filter(s -> !s.isEmpty()).map(Path::of).toList();
    }

    /** Short description for messages: "FooTest.gs" or "2 folders, 1 file". */
    static String describe(List<Path> tests) {
        if (tests.size() == 1) {
            return tests.get(0).getFileName().toString() + (Files.isDirectory(tests.get(0)) ? " (folder)" : "");
        }
        long folders = tests.stream().filter(Files::isDirectory).count();
        long files = tests.size() - folders;
        return (folders > 0 ? folders + (folders == 1 ? " folder" : " folders") : "")
                + (folders > 0 && files > 0 ? ", " : "")
                + (files > 0 ? files + (files == 1 ? " file" : " files") : "");
    }
}
