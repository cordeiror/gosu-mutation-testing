package io.gosumutation.intellij;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Builds the AI stub request, copies it to the clipboard and shows it in an editor tab. */
final class StubRequest {

    private StubRequest() { }

    static void open(Project project, Path source, List<Path> tests, TrialPaths paths) {
        String failure = MutationRunner.read(paths.baselineFailure());
        if (failure == null) {
            MutationRunner.notify(project, "Run Mutation Testing first: there is no baseline failure for "
                    + source.getFileName() + " in " + paths.reportDir(), NotificationType.WARNING);
            return;
        }
        String sourceText = MutationRunner.read(source);
        if (sourceText == null) {
            MutationRunner.notify(project, "Could not read " + source, NotificationType.ERROR);
            return;
        }
        String request = StubRequestBuilder.build(new StubRequestBuilder.Input(source, sourceText, testSources(tests),
                failure, paths.stubsDir(), existingStubs(paths.stubsDir()), guide()));

        AiRequests.show(project, "stub-request-" + source.getFileName() + ".md", request, "AI stub request",
                "run Run Mutation Testing again");
    }

    /** The Gosu files of the chosen tests; folders are searched recursively. */
    static List<StubRequestBuilder.SourceFile> testSources(List<Path> tests) {
        List<StubRequestBuilder.SourceFile> sources = new java.util.ArrayList<>();
        for (Path test : tests) {
            if (Files.isDirectory(test)) {
                try (Stream<Path> files = Files.walk(test)) {
                    files.filter(p -> p.toString().endsWith(".gs") || p.toString().endsWith(".gsx")).sorted()
                            .forEach(p -> addIfReadable(sources, p));
                } catch (IOException e) {
                    // Unreadable folder: the request simply lists fewer tests.
                }
            } else {
                addIfReadable(sources, test);
            }
        }
        return sources;
    }

    private static void addIfReadable(List<StubRequestBuilder.SourceFile> sources, Path file) {
        String text = MutationRunner.read(file);
        if (text != null) {
            sources.add(new StubRequestBuilder.SourceFile(file, text));
        }
    }

    private static List<String> existingStubs(Path stubsDir) {
        if (!Files.isDirectory(stubsDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(stubsDir)) {
            return files.filter(p -> p.toString().endsWith(".gs"))
                    .map(p -> stubsDir.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static String guide() {
        try (InputStream in = StubRequest.class.getResourceAsStream("stubbing-guide.md")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
