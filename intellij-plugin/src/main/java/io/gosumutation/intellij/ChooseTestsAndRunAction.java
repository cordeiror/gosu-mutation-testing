package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;
import java.util.List;

/** Right-click a .gs file: Run Mutation Testing with Chosen Tests... (always asks, and remembers the choice). */
public final class ChooseTestsAndRunAction extends AnAction {

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
        List<Path> previous = TestSelection.remembered(settings.chosenTests, source);
        Path startAt = previous.isEmpty() ? TestFileLocator.suggestedFolder(source, settings.testMappings)
                : previous.get(0).getParent();

        List<Path> chosen = TestChooser.choose(project, file, startAt);
        if (chosen == null) {
            return;
        }
        TestSelection.remember(settings.chosenTests, source, chosen);
        RunMutationTestingAction.start(project, source, chosen);
    }
}
