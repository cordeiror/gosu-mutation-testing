package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;

/** Right-click a .gs file: Prepare Class (Stubs Wizard)... for the first run of a class from a large code base. */
public final class PrepareClassAction extends AnAction {

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
        StubWizardDialog.open(project, source, TestSelection.resolve(source, settings.chosenTests, settings.testMappings), false);
    }
}
