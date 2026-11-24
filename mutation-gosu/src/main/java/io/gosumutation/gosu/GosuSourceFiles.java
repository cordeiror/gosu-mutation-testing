package io.gosumutation.gosu;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Locates Gosu source files and maps them to type names. */
public final class GosuSourceFiles {

    private GosuSourceFiles() { }

    /** Gosu classes ({@code .gs}), sorted for deterministic processing. */
    public static List<Path> classFiles(Path sourceRoot) {
        return find(sourceRoot, ".gs");
    }

    /** Everything the Gosu compiler must be given: classes and enhancements. */
    public static List<Path> compilableFiles(Path sourceRoot) {
        return find(sourceRoot, ".gs", ".gsx");
    }

    private static List<Path> find(Path sourceRoot, String... extensions) {
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        for (String extension : extensions) {
                            if (name.endsWith(extension)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code demo/Foo.gs} for a file below {@code sourceRoot}, always with '/' separators. */
    public static String relativePath(Path sourceRoot, Path file) {
        return sourceRoot.relativize(file).toString().replace('\\', '/');
    }

    /** {@code demo.Foo} for {@code demo/Foo.gs}. */
    public static String typeName(String relativePath) {
        return relativePath.substring(0, relativePath.lastIndexOf('.')).replace('/', '.');
    }
}
