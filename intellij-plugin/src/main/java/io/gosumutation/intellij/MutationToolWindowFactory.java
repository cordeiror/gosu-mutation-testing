package io.gosumutation.intellij;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/** Creates the Gosu Mutation tool window. */
public final class MutationToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        Content content = ContentFactory.getInstance().createContent(null, "", false);
        content.setComponent(new MutationResultsPanel(project, content));
        toolWindow.getContentManager().addContent(content);
    }
}
