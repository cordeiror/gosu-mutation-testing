package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;
import java.util.Optional;

/** Right-click a .gs file: Run Mutation Testing. */
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
        // The tool reads the files from disk.
        FileDocumentManager.getInstance().saveAllDocuments();
        Path source = GosuFiles.pathOf(file);
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();

        Path test = TestFileLocator.find(source, settings.testMappings).orElse(null);
        if (test == null) {
            Optional<Path> chosen = askForTest(project, file);
            if (chosen == null) {
                return; // cancelled
            }
            test = chosen.orElse(null);
        }
        MutationRunner.run(project, source, test);
    }

    /** @return null when cancelled, empty to use the tests already in the trial project */
    private static Optional<Path> askForTest(Project project, VirtualFile file) {
        int answer = Messages.showYesNoCancelDialog(project,
                "No test was found for " + file.getName() + " with the configured test folder mappings.\n"
                        + "Choose the test file, or run with the tests already in the trial project?",
                "Gosu Mutation Testing", "Choose Test File...", "Use Trial Project Tests", "Cancel", null);
        if (answer == Messages.CANCEL) {
            return null;
        }
        if (answer == Messages.NO) {
            return Optional.empty();
        }
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Choose the Test of " + file.getName())
                .withFileFilter(candidate -> "gs".equals(candidate.getExtension()));
        VirtualFile chosen = FileChooser.chooseFile(descriptor, project, file.getParent());
        return chosen == null ? null : Optional.of(GosuFiles.pathOf(chosen));
    }
}
