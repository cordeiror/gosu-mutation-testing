package io.gosumutation.intellij;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.LightVirtualFile;

import java.awt.datatransfer.StringSelection;

/** Hands a request to the user's AI assistant: clipboard, an editor tab and, when installed, Copilot Chat. */
final class AiRequests {

    private AiRequests() { }

    /**
     * @param afterwards what the user should do once the assistant is done, e.g. "run Run Mutation Testing again"
     */
    static void show(Project project, String fileName, String request, String what, String afterwards) {
        CopyPasteManager.getInstance().setContents(new StringSelection(request));
        FileEditorManager.getInstance(project).openFile(new LightVirtualFile(fileName, request), true);
        boolean chatOpened = activateCopilotChat(project);
        MutationRunner.notify(project, "The " + what + " was copied to the clipboard. Paste it into "
                + (chatOpened ? "the Copilot Chat window" : "Copilot Chat")
                + " in agent mode so it can edit the files, then " + afterwards + ".", NotificationType.INFORMATION);
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
}
