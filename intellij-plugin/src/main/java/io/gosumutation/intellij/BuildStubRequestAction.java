package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Files;
import java.nio.file.Path;

/** Right-click a .gs file: Build AI Stub Request (after a run whose baseline did not compile). */
public final class BuildStubRequestAction extends AnAction {

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent event) {
        VirtualFile file = GosuFiles.selectedClass(event);
        boolean visible = event.getProject() != null && file != null;
        event.getPresentation().setVisible(visible);
        if (visible) {
            GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
            TrialPaths paths = TrialPaths.of(GosuFiles.pathOf(file), settings.trialsRoot, settings.stubsDir);
            event.getPresentation().setEnabled(Files.isRegularFile(paths.baselineFailure()));
        }
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
        StubRequest.open(project, source, TestSelection.resolve(source, settings.chosenTests, settings.testMappings),
                TrialPaths.of(source, settings.trialsRoot, settings.stubsDir));
    }
}
