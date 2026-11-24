package io.gosumutation.intellij;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.LightVirtualFile;

import java.awt.datatransfer.StringSelection;
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

    static void open(Project project, Path source, Path test, TrialPaths paths) {
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
        String request = StubRequestBuilder.build(new StubRequestBuilder.Input(source, sourceText, test,
                test == null ? null : MutationRunner.read(test), failure, paths.stubsDir(), existingStubs(paths.stubsDir()),
                guide()));

        CopyPasteManager.getInstance().setContents(new StringSelection(request));
        LightVirtualFile file = new LightVirtualFile("stub-request-" + source.getFileName() + ".md", request);
        FileEditorManager.getInstance(project).openFile(file, true);
        boolean chatOpened = activateCopilotChat(project);
        MutationRunner.notify(project, "The AI stub request was copied to the clipboard. Paste it into "
                + (chatOpened ? "the Copilot Chat window" : "Copilot Chat")
                + " in agent mode so it can create the files, then run Run Mutation Testing again.",
                NotificationType.INFORMATION);
    }

    /** Best effort: brings a Copilot chat tool window to front when one is installed. */
    private static boolean activateCopilotChat(Project project) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        for (String id : manager.getToolWindowIds()) {
            String lower = id.toLowerCase();
            if (lower.contains("copilot") && lower.contains("chat")) {
                ToolWindow window = manager.getToolWindow(id);
                if (window != null) {
                    window.activate(null);
                    return true;
                }
            }
        }
        return false;
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
