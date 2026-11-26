package io.gosumutation.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** The Gosu Mutation tool window: the mutants of the last class run, with what to do about them. */
final class MutationResultsPanel extends SimpleToolWindowPanel implements MutationResultsService.Listener {

    private final Project project;
    private final JBLabel header = new JBLabel("Run Mutation Testing on a Gosu class, or use Show Mutation Results.");
    private final Model model = new Model();
    private final JBTable table = new JBTable(model);
    private final JBTextArea details = new JBTextArea();
    private MutationResultsService.Loaded loaded;

    MutationResultsPanel(Project project, Disposable parent) {
        super(true, true);
        this.project = project;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(50);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(420);
        table.getSelectionModel().addListSelectionListener(e -> showDetails());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigate();
                }
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigate();
                    e.consume();
                }
            }
        });
        details.setEditable(false);
        details.setFont(JBUI.Fonts.create(java.awt.Font.MONOSPACED, details.getFont().getSize()));

        JBSplitter splitter = new JBSplitter(false, 0.65f);
        splitter.setFirstComponent(new JBScrollPane(table));
        splitter.setSecondComponent(new JBScrollPane(details));
        JPanel content = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(4, 8));
        content.add(header, BorderLayout.NORTH);
        content.add(splitter, BorderLayout.CENTER);
        setContent(content);

        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(new Rerun());
        actions.add(new ToggleEquivalent());
        actions.add(new TestRequest());
        actions.add(new OpenHtmlReport());
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("GosuMutationResults", actions, true);
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());

        MutationResultsService service = MutationResultsService.getInstance(project);
        service.addListener(this, parent);
        if (service.current() != null) {
            resultsChanged(service.current());
        }
    }

    @Override
    public void resultsChanged(MutationResultsService.Loaded loaded) {
        String selectedId = selected() == null ? null : selected().id();
        this.loaded = loaded;
        header.setText("<html><b>" + loaded.source().getFileName() + "</b> — " + loaded.results().summary()
                + " <span style='color:gray'>(run " + loaded.results().generatedAt().replace('T', ' ')
                .replaceAll("\\..*", "") + " UTC)</span></html>");
        model.setMutants(loaded.results().mutants());
        if (selectedId != null) {
            for (int i = 0; i < model.mutants.size(); i++) {
                if (model.mutants.get(i).id().equals(selectedId)) {
                    table.getSelectionModel().setSelectionInterval(i, i);
                }
            }
        }
        showDetails();
    }

    void selectLine(int line) {
        for (int i = 0; i < model.mutants.size(); i++) {
            if (model.mutants.get(i).line() == line) {
                table.getSelectionModel().setSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private MutationResults.Mutant selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.mutants.size() ? null : model.mutants.get(table.convertRowIndexToModel(row));
    }

    private void showDetails() {
        MutationResults.Mutant m = selected();
        if (m == null) {
            details.setText(loaded == null ? "" : "Select a mutant to see the change.\n\n"
                    + "Double-click (or Enter) goes to the line.\n"
                    + "Mark as Equivalent: for a survivor that no test can detect.\n"
                    + "Build AI Test Request: asks an AI assistant for tests that kill the survivors.");
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(MutationResults.label(m.status())).append(" — line ").append(m.line()).append(", ").append(m.method()).append('\n');
        text.append(m.description()).append("\n\n");
        text.append("- ").append(m.originalLine()).append('\n');
        text.append("+ ").append(m.mutatedLine()).append("\n\n");
        text.append("Operator: ").append(m.operator()).append("\nMutant id: ").append(m.id()).append('\n');
        if (!m.killingTests().isEmpty()) {
            text.append("\nKilled by:\n");
            m.killingTests().forEach(test -> text.append("  ").append(test).append('\n'));
        }
        if (MutationResults.EQUIVALENT.equals(m.status()) && !MutationResults.EQUIVALENT.equals(m.reportStatus())) {
            text.append("\nMarked as equivalent: the next run skips it and leaves it out of the score.\n");
        }
        if (MutationResults.RUN_AGAIN.equals(m.status())) {
            text.append("\nNo longer marked as equivalent: run again to evaluate it.\n");
        }
        if (!m.details().isBlank()) {
            text.append('\n').append(m.details()).append('\n');
        }
        details.setText(text.toString());
        details.setCaretPosition(0);
    }

    private void navigate() {
        MutationResults.Mutant m = selected();
        if (m == null || loaded == null) {
            return;
        }
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(loaded.source());
        if (file != null) {
            new OpenFileDescriptor(project, file, Math.max(0, m.line() - 1), Math.max(0, m.column() - 1)).navigate(true);
        }
    }

    private abstract class PanelAction extends DumbAwareAction {
        PanelAction(String text, String description, javax.swing.Icon icon) {
            super(text, description, icon);
        }

        @Override
        public ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    private final class Rerun extends PanelAction {
        Rerun() {
            super("Run Again", "Run mutation testing on this class again", AllIcons.Actions.Rerun);
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(loaded != null);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
            RunMutationTestingAction.start(project, loaded.source(),
                    TestSelection.resolve(loaded.source(), settings.chosenTests, settings.testMappings));
        }
    }

    private final class ToggleEquivalent extends PanelAction {
        ToggleEquivalent() {
            super("Mark as Equivalent", "No test can detect this mutant: skip it and leave it out of the score",
                    AllIcons.Actions.Checked);
        }

        @Override
        public void update(AnActionEvent e) {
            MutationResults.Mutant m = selected();
            boolean marked = m != null && MutationResults.EQUIVALENT.equals(m.status());
            e.getPresentation().setText(marked ? "Unmark as Equivalent" : "Mark as Equivalent");
            e.getPresentation().setEnabled(m != null && (marked || !MutationResults.KILLED.equals(m.status())));
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            MutationResults.Mutant m = selected();
            if (m == null || loaded == null) {
                return;
            }
            try {
                MutationResultsService.getInstance(project).setEquivalent(loaded, m, !MutationResults.EQUIVALENT.equals(m.status()));
            } catch (IOException ex) {
                MutationRunner.notify(project, "Could not update " + loaded.paths().equivalentFile() + ": " + ex.getMessage(),
                        NotificationType.ERROR);
            }
        }
    }

    private final class TestRequest extends PanelAction {
        TestRequest() {
            super("Build AI Test Request", "Ask an AI assistant for tests that kill the surviving mutants",
                    AllIcons.Actions.IntentionBulb);
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(loaded != null && !loaded.results().undetected().isEmpty());
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            TestRequestAction.open(project, loaded);
        }
    }

    private final class OpenHtmlReport extends PanelAction {
        OpenHtmlReport() {
            super("Open HTML Report", "Open the full report in the browser", AllIcons.Xml.Browsers.Chrome);
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(loaded != null && Files.isRegularFile(loaded.paths().htmlReport()));
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            BrowserUtil.browse(loaded.paths().htmlReportUri());
        }
    }

    private static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {"Result", "Line", "Method", "Mutation", "Killed by"};
        private List<MutationResults.Mutant> mutants = List.of();

        void setMutants(List<MutationResults.Mutant> mutants) {
            this.mutants = mutants;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return mutants.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            MutationResults.Mutant m = mutants.get(row);
            return switch (column) {
                case 0 -> MutationResults.label(m.status());
                case 1 -> m.line();
                case 2 -> m.method();
                case 3 -> m.description() + "   " + m.mutatedLine();
                default -> String.join(", ", m.killingTests().stream()
                        .map(t -> t.substring(t.lastIndexOf('.', Math.max(0, t.indexOf('(') - 1)) + 1)).toList());
            };
        }
    }
}
