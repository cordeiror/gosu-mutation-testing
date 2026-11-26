package io.gosumutation.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Guides the user through making a class compile in its trial project: what stubs are and why
 * they are needed, where they go, and a loop of "ask an AI assistant (or write them by hand),
 * check again" until the class compiles and its tests pass. Non-modal, so the AI chat can be used
 * while it is open.
 */
final class StubWizardDialog extends DialogWrapper {

    private static final String[] STEPS = {"intro", "setup", "check"};
    private static final String[] TITLES = {"1. Why stubs", "2. Tests and stubs folder", "3. Create stubs and check"};

    private final Project project;
    private final Path source;
    private final String className;
    private List<Path> tests;
    private int step;

    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final JBLabel stepTitle = new JBLabel();
    private final JBLabel testsLabel = new JBLabel();
    private final TextFieldWithBrowseButton stubsField = new TextFieldWithBrowseButton();
    private final JEditorPane status = html("");
    private final JButton checkButton = new JButton("Check Now");
    private final JButton requestButton = new JButton("Copy AI Stub Request");
    private final List<Integer> history = new ArrayList<>();
    private StubCheck lastCheck;
    private boolean checking;

    private final Action backAction = new AbstractAction("Back") {
        @Override
        public void actionPerformed(ActionEvent e) {
            go(step - 1);
        }
    };
    private final Action nextAction = new AbstractAction("Next") {
        @Override
        public void actionPerformed(ActionEvent e) {
            go(step + 1);
        }
    };

    StubWizardDialog(Project project, Path source, List<Path> tests, boolean startAtCheck) {
        super(project, null, true, IdeModalityType.MODELESS);
        this.project = project;
        this.source = source;
        this.className = source.getFileName().toString();
        this.tests = tests;
        setTitle("Prepare " + className + " for Mutation Testing");
        setOKButtonText("Run Mutation Testing");
        setCancelButtonText("Close");
        init();
        go(startAtCheck ? 2 : 0);
        if (startAtCheck) {
            showExistingFailure();
        }
    }

    /** Opens the wizard; {@code startAtCheck} skips the explanations (used after a failed run). */
    static void open(Project project, Path source, List<Path> tests, boolean startAtCheck) {
        new StubWizardDialog(project, source, tests, startAtCheck).show();
    }

    @Override
    protected Action[] createActions() {
        return new Action[] {backAction, nextAction, getOKAction(), getCancelAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
        pages.add(introPage(), STEPS[0]);
        pages.add(setupPage(), STEPS[1]);
        pages.add(checkPage(), STEPS[2]);
        stepTitle.setFont(JBUI.Fonts.label().biggerOn(2).asBold());
        stepTitle.setBorder(JBUI.Borders.emptyBottom(8));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(stepTitle, BorderLayout.NORTH);
        panel.add(pages, BorderLayout.CENTER);
        panel.setPreferredSize(JBUI.size(720, 480));
        return panel;
    }

    private JComponent introPage() {
        return new JBScrollPane(html(StubWizardTexts.intro(className)));
    }

    private JComponent setupPage() {
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
        TrialPaths paths = paths();
        stubsField.setText(settings.stubsDir.isBlank() ? paths.stubsDir().toString() : settings.stubsDir);
        stubsField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Stubs Folder").withDescription("Folder where the stand-in types are written"));
        JButton chooseTests = new JButton("Choose Tests...");
        chooseTests.addActionListener(e -> {
            VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(source);
            List<Path> chosen = file == null ? null : TestChooser.choose(project, file,
                    TestFileLocator.suggestedFolder(source, settings.testMappings));
            if (chosen != null) {
                TestSelection.remember(settings.chosenTests, source, chosen);
                tests = chosen;
                updateTestsLabel();
            }
        });
        updateTestsLabel();
        JPanel testsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testsRow.add(testsLabel);
        testsRow.add(javax.swing.Box.createHorizontalStrut(12));
        testsRow.add(chooseTests);
        return FormBuilder.createFormBuilder()
                .addComponent(html(StubWizardTexts.setup()))
                .addLabeledComponent("Class:", new JBLabel(source.toString()))
                .addLabeledComponent("Tests:", testsRow)
                .addLabeledComponent("Stubs folder:", stubsField)
                .addComponentToRightColumn(hint("Shared by all classes, so stubs created for one class help the next. "
                        + "Keep it outside the original project and outside version control."))
                .addLabeledComponent("Trial project:", new JBLabel(paths.trialDir().toString()))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private JComponent checkPage() {
        checkButton.addActionListener(e -> check());
        requestButton.addActionListener(e -> StubRequest.open(project, source, tests, paths()));
        JButton guide = new JButton("Open Stubbing Guide");
        guide.addActionListener(e -> FileEditorManager.getInstance(project)
                .openFile(new LightVirtualFile("stubbing-guide.md", StubRequest.guide()), true));
        JButton folder = new JButton("Open Stubs Folder");
        folder.addActionListener(e -> {
            try {
                Files.createDirectories(paths().stubsDir());
                RevealFileAction.openDirectory(paths().stubsDir());
            } catch (java.io.IOException ex) {
                MutationRunner.notify(project, "Could not open " + paths().stubsDir() + ": " + ex.getMessage(),
                        com.intellij.notification.NotificationType.ERROR);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(checkButton);
        buttons.add(requestButton);
        buttons.add(guide);
        buttons.add(folder);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JBScrollPane(status), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        status.setText(StubWizardTexts.beforeCheck(className));
        return panel;
    }

    private void go(int target) {
        if (step == 1 && target > 1) {
            saveStubsFolder();
        }
        step = Math.max(0, Math.min(STEPS.length - 1, target));
        cards.show(pages, STEPS[step]);
        stepTitle.setText(TITLES[step]);
        updateButtons();
        if (step == 2 && lastCheck == null && !checking && !Files.isRegularFile(paths().baselineFailure())) {
            check();
        }
    }

    private void updateButtons() {
        backAction.setEnabled(step > 0 && !checking);
        nextAction.setEnabled(step < STEPS.length - 1);
        checkButton.setEnabled(!checking);
        requestButton.setEnabled(!checking && lastCheck != null
                && (lastCheck.kind() == StubCheck.Kind.DOES_NOT_COMPILE || lastCheck.kind() == StubCheck.Kind.TESTS_FAIL));
        setOKActionEnabled(!checking && lastCheck != null && lastCheck.kind() == StubCheck.Kind.READY);
    }

    private void saveStubsFolder() {
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
        String chosen = stubsField.getText().trim();
        Path defaultStubs = TrialPaths.of(source, settings.trialsRoot, "").stubsDir();
        settings.stubsDir = chosen.isEmpty() || Path.of(chosen).toAbsolutePath().normalize().equals(defaultStubs) ? "" : chosen;
        try {
            Files.createDirectories(paths().stubsDir());
        } catch (java.io.IOException e) {
            // Reported when the check runs.
        }
    }

    private TrialPaths paths() {
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
        return TrialPaths.of(source, settings.trialsRoot, settings.stubsDir);
    }

    private void updateTestsLabel() {
        testsLabel.setText(tests.isEmpty() ? "none chosen: the tests already in the trial project are used"
                : TestSelection.describe(tests));
    }

    /** A failed run already left its errors: show them without running again. */
    private void showExistingFailure() {
        String failure = MutationRunner.read(paths().baselineFailure());
        if (failure != null) {
            show(StubCheck.of(1, failure, MutationRunner.read(paths().lastRunLog()), declaredSources()));
        }
    }

    private void check() {
        FileDocumentManager.getInstance().saveAllDocuments();
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
        TrialPaths paths = paths();
        Path cliLib = MutationRunner.cliLib(settings);
        if (cliLib == null || !Files.isDirectory(cliLib)) {
            status.setText(StubWizardTexts.problem("The gosu-mutation jars were not found" + (cliLib == null ? "." : " in " + cliLib + ".")));
            return;
        }
        Path javaHome = settings.javaHome.isBlank() ? Path.of(System.getProperty("java.home")) : Path.of(settings.javaHome);
        List<String> command = MutationCommand.build(new MutationCommand.Request(javaHome, cliLib, source, tests,
                paths.trialDir(), paths.stubsDirConfigured() ? paths.stubsDir() : null,
                settings.extraArgs + " --baseline-only"));
        checking = true;
        updateButtons();
        status.setText(StubWizardTexts.checking(className));
        new Task.Backgroundable(project, "Checking " + className + " in its trial project", true) {
            private StubCheck result;

            @Override
            public void run(ProgressIndicator indicator) {
                try {
                    Files.createDirectories(paths.stubsDir());
                    Files.createDirectories(paths.trialDir());
                    Files.deleteIfExists(paths.baselineFailure());
                    GeneralCommandLine commandLine = new GeneralCommandLine(command)
                            .withWorkDirectory(paths.trialDir().toFile())
                            .withCharset(StandardCharsets.UTF_8);
                    ProcessOutput output = new CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator);
                    String text = output.getStdout() + output.getStderr();
                    Files.createDirectories(paths.lastRunLog().getParent());
                    Files.writeString(paths.lastRunLog(), text, StandardCharsets.UTF_8);
                    result = output.isCancelled() ? null
                            : StubCheck.of(output.getExitCode(), MutationRunner.read(paths.baselineFailure()), text, declaredSources());
                } catch (ExecutionException | java.io.IOException e) {
                    result = new StubCheck(StubCheck.Kind.OTHER, 0, new BaselineErrors.Missing(List.of(), List.of()),
                            "Could not start gosu-mutation: " + e.getMessage());
                }
            }

            @Override
            public void onFinished() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    checking = false;
                    if (result != null) {
                        StubWizardDialog.this.show(result);
                    } else {
                        status.setText(StubWizardTexts.beforeCheck(className));
                    }
                    updateButtons();
                });
            }
        }.queue();
    }

    private void show(StubCheck check) {
        lastCheck = check;
        if (check.kind() == StubCheck.Kind.DOES_NOT_COMPILE) {
            history.add(check.errorCount());
        } else if (check.kind() == StubCheck.Kind.READY || check.kind() == StubCheck.Kind.TESTS_FAIL) {
            history.add(0);
        }
        status.setText(StubWizardTexts.result(className, check, history, paths().stubsDir()));
        status.setCaretPosition(0);
        updateButtons();
    }

    /** The class and its tests, so names they declare themselves are not reported as missing. */
    private List<String> declaredSources() {
        List<String> sources = new ArrayList<>();
        sources.add(MutationRunner.read(source));
        StubRequest.testSources(tests).forEach(file -> sources.add(file.text()));
        return sources;
    }

    @Override
    protected void doOKAction() {
        super.doOKAction();
        RunMutationTestingAction.start(project, source, tests);
    }

    private static JEditorPane html(String text) {
        JEditorPane pane = new JEditorPane("text/html", text);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(JBUI.Borders.empty(4));
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(JBUI.Fonts.label());
        return pane;
    }

    private static JBLabel hint(String text) {
        JBLabel label = new JBLabel(text);
        label.setComponentStyle(com.intellij.util.ui.UIUtil.ComponentStyle.SMALL);
        label.setFontColor(com.intellij.util.ui.UIUtil.FontColor.BRIGHTER);
        return label;
    }
}
