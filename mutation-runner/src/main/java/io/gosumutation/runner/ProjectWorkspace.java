package io.gosumutation.runner;

import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.MutationDescriptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * A throwaway copy of the project sources in a temporary directory. This is what guarantees that
 * the original files are never modified and that mutants cannot affect each other: every mutant is
 * compiled and tested inside its own workspace, which is deleted afterwards.
 */
public final class ProjectWorkspace implements AutoCloseable {

    private final Path root;
    private final List<Path> sourceRoots = new ArrayList<>();

    private ProjectWorkspace(Path root) {
        this.root = root;
    }

    /**
     * Copies the source and test directories.
     *
     * @param mutant when not null, the copy of its file receives the mutation
     */
    public static ProjectWorkspace create(MutationConfiguration configuration, MutationDescriptor mutant) throws IOException {
        ProjectWorkspace workspace = new ProjectWorkspace(Files.createTempDirectory("gosu-mutation-"));
        try {
            boolean applied = false;
            int index = 0;
            for (Path sourceDir : configuration.sourceDirs()) {
                Path copy = workspace.root.resolve("main-" + index++);
                copyTree(sourceDir, copy);
                workspace.sourceRoots.add(copy);
                if (mutant != null && !applied && sourceDir.toString().equals(mutant.sourceRoot())) {
                    Path file = copy.resolve(mutant.relativePath());
                    String original = Files.readString(file, StandardCharsets.UTF_8);
                    Files.writeString(file, mutant.applyTo(original), StandardCharsets.UTF_8);
                    applied = true;
                }
            }
            if (mutant != null && !applied) {
                throw new IOException("Source root of mutant " + mutant.id() + " is not configured: " + mutant.sourceRoot());
            }
            index = 0;
            for (Path testDir : configuration.testDirs()) {
                Path copy = workspace.root.resolve("test-" + index++);
                copyTree(testDir, copy);
                workspace.sourceRoots.add(copy);
            }
            return workspace;
        } catch (IOException | RuntimeException e) {
            workspace.close();
            throw e;
        }
    }

    /** Copied production and test source directories. */
    public List<Path> sourceRoots() {
        return List.copyOf(sourceRoots);
    }

    public Path classesDir() {
        return root.resolve("classes");
    }

    public Path workDir() {
        return root.resolve("work");
    }

    @Override
    public void close() {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best effort: the directory lives in the system temp folder.
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        if (!Files.isDirectory(from)) {
            return;
        }
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
