package io.gosumutation.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;

/** Explains each option of the menu: IntelliJ shows no tooltips in context menus. */
public final class HelpAction extends DumbAwareAction {

    public HelpAction() {
        super(AllIcons.Actions.Help);
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        new DialogWrapper(event.getProject()) {
            {
                setTitle("Gosu Mutation Testing: What the Options Do");
                init();
            }

            @Override
            protected JComponent createCenterPanel() {
                JEditorPane pane = new JEditorPane("text/html", PluginHelp.html());
                pane.setEditable(false);
                pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
                pane.setFont(JBUI.Fonts.label());
                pane.setCaretPosition(0);
                JBScrollPane scroll = new JBScrollPane(pane);
                scroll.setPreferredSize(JBUI.size(620, 520));
                return scroll;
            }

            @Override
            protected Action[] createActions() {
                return new Action[] {getOKAction()};
            }
        }.show();
    }
}
