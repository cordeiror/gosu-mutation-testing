package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Path;

/** Right-click a .gs file: Show Mutation Results of its last run, without running again. */
public final class ShowMutationResultsAction extends AnAction {

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
        show(project, GosuFiles.pathOf(file), true);
    }

    /** Loads and shows the last results; explains when there are none if {@code explain} is set. */
    static boolean show(Project project, Path source, boolean explain) {
        try {
            MutationResultsService service = MutationResultsService.getInstance(project);
            if (service.load(source) == null) {
                if (explain) {
                    Messages.showInfoMessage(project, source.getFileName() + " has no mutation results yet.\n\n"
                            + "Run \"Run Mutation Testing\" first.", "Gosu Mutation Testing");
                }
                return false;
            }
            service.showToolWindow(0);
            return true;
        } catch (IOException | RuntimeException e) {
            Messages.showErrorDialog(project, "Could not read the results of " + source.getFileName() + ": " + e.getMessage(),
                    "Gosu Mutation Testing");
            return false;
        }
    }
}
