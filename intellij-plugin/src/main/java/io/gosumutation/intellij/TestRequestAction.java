package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Right-click a .gs file: Build AI Test Request. Asks an AI assistant for tests that kill the
 * mutants that survived the last run. Always enabled on Gosu classes; explains when there is
 * nothing to ask for.
 */
public final class TestRequestAction extends AnAction {

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && GosuFiles.selectedClass(event) != null);
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile file = GosuFiles.selectedClass(event);
        if (project == null || file == null) {
            return;
        }
        Path source = GosuFiles.pathOf(file);
        MutationResultsService.Loaded loaded;
        try {
            loaded = MutationResultsService.getInstance(project).load(source);
        } catch (IOException | RuntimeException e) {
            Messages.showErrorDialog(project, "Could not read the results of " + file.getName() + ": " + e.getMessage(),
                    "Build AI Test Request");
            return;
        }
        if (loaded == null) {
            Messages.showInfoMessage(project, file.getName() + " has no mutation results yet.\n\n"
                    + "Run \"Run Mutation Testing\" first; this action then asks an AI assistant for tests that "
                    + "detect the mutants that survived.", "Build AI Test Request");
            return;
        }
        open(project, loaded);
    }

    static void open(Project project, MutationResultsService.Loaded loaded) {
        List<MutationResults.Mutant> undetected = loaded.results().undetected();
        String name = loaded.source().getFileName().toString();
        if (undetected.isEmpty()) {
            Messages.showInfoMessage(project, "Every evaluated mutant of " + name + " was killed (or marked as equivalent), "
                    + "so there is nothing to ask for.\n\n" + loaded.results().summary(), "Build AI Test Request");
            return;
        }
        String sourceText = MutationRunner.read(loaded.source());
        if (sourceText == null) {
            Messages.showErrorDialog(project, "Could not read " + loaded.source(), "Build AI Test Request");
            return;
        }
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
        List<Path> tests = TestSelection.resolve(loaded.source(), settings.chosenTests, settings.testMappings);
        if (tests.isEmpty() && java.nio.file.Files.isDirectory(loaded.paths().trialDir().resolve("src/test/gosu"))) {
            tests = List.of(loaded.paths().trialDir().resolve("src/test/gosu")); // the tests the run used
        }
        String request = TestRequestBuilder.build(new TestRequestBuilder.Input(loaded.source(), sourceText, undetected,
                StubRequest.testSources(tests)));
        AiRequests.show(project, "test-request-" + name + ".md", request, "AI test request",
                "run Run Mutation Testing again to check that the new tests kill the mutants");
    }
}
