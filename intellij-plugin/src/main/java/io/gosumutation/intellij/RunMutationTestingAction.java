package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;
import java.util.List;

/**
 * Right-click a .gs file: Run Mutation Testing. Uses the tests chosen earlier for the class, or the
 * test found through the folder mappings, or asks.
 */
public final class RunMutationTestingAction extends AnAction {

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
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();

        List<Path> tests = TestSelection.resolve(source, settings.chosenTests, settings.testMappings);
        if (tests.isEmpty()) {
            tests = askForTests(project, file, source, settings);
            if (tests == null) {
                return; // cancelled
            }
        }
        if (settings.offerWizardOnFirstRun && neverRun(source, settings)) {
            int answer = com.intellij.openapi.ui.MessageDialogBuilder.yesNoCancel("First Run of " + file.getName(),
                            "This is the first mutation testing run of " + file.getName() + ".\n\n"
                            + "Classes from a large code base often use types that the trial project does not have, so "
                            + "they do not compile there until minimal stand-ins (stubs) are created. The wizard explains "
                            + "this, checks the class in a few seconds and helps an AI assistant create the stubs.\n\n"
                            + "Prepare with the wizard, or run now?")
                    .yesText("Prepare with Wizard")
                    .noText("Run Now")
                    .cancelText("Cancel")
                    .doNotAsk(new com.intellij.openapi.ui.DoNotAskOption.Adapter() {
                        @Override
                        public void rememberChoice(boolean isSelected, int exitCode) {
                            if (isSelected) {
                                settings.offerWizardOnFirstRun = false;
                            }
                        }
                    })
                    .show(project);
            if (answer == Messages.CANCEL) {
                return;
            }
            if (answer == Messages.YES) {
                StubWizardDialog.open(project, source, tests, false);
                return;
            }
        }
        start(project, source, tests);
    }

    /** No earlier run left anything in the trial project of the class. */
    static boolean neverRun(Path source, GosuMutationSettings.State settings) {
        TrialPaths paths = TrialPaths.of(source, settings.trialsRoot, settings.stubsDir);
        return !java.nio.file.Files.exists(paths.jsonReport()) && !java.nio.file.Files.exists(paths.baselineFailure())
                && !java.nio.file.Files.exists(paths.lastRunLog());
    }

    /** Saves the editors (the tool reads the files from disk) and runs. */
    static void start(Project project, Path source, List<Path> tests) {
        FileDocumentManager.getInstance().saveAllDocuments();
        MutationRunner.run(project, source, tests);
    }

    /** @return the chosen tests (remembered for next time), empty to use the trial project's tests, null when cancelled */
    private static List<Path> askForTests(Project project, VirtualFile file, Path source, GosuMutationSettings.State settings) {
        int answer = Messages.showYesNoCancelDialog(project,
                "No test was found for " + file.getName() + " with the configured test folder mappings.\n"
                        + "Choose its tests (files and/or folders; folders are searched recursively), "
                        + "or run with the tests already in the trial project?",
                "Gosu Mutation Testing", "Choose Tests...", "Use Trial Project Tests", "Cancel", null);
        if (answer == Messages.CANCEL) {
            return null;
        }
        if (answer == Messages.NO) {
            return List.of();
        }
        List<Path> chosen = TestChooser.choose(project, file, TestFileLocator.suggestedFolder(source, settings.testMappings));
        if (chosen != null) {
            TestSelection.remember(settings.chosenTests, source, chosen);
        }
        return chosen;
    }
}
