package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;

/**
 * Right-click a .gs file: Build AI Stub Request. Always enabled on Gosu classes; when there is no
 * failed run to work from, it explains why instead of doing nothing.
 */
public final class BuildStubRequestAction extends AnAction {

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
        TrialPaths paths = TrialPaths.of(source, settings.trialsRoot, settings.stubsDir);
        StubRequestStatus status = StubRequestStatus.of(paths, file.getName());
        if (!status.canBuild()) {
            Messages.showInfoMessage(project, status.message(), "Build AI Stub Request");
            return;
        }
        StubRequest.open(project, source, TestSelection.resolve(source, settings.chosenTests, settings.testMappings), paths);
    }
}
