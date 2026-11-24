package io.gosumutation.intellij;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.Objects;

/** Settings | Tools | Gosu Mutation Testing. */
public final class GosuMutationConfigurable implements Configurable {

    private JBTextField trialsRoot;
    private JBTextField stubsDir;
    private JBTextField testMappings;
    private JBTextField javaHome;
    private JBTextField cliLib;
    private JBTextField extraArgs;

    @Override
    public String getDisplayName() {
        return "Gosu Mutation Testing";
    }

    @Override
    public JComponent createComponent() {
        trialsRoot = new JBTextField();
        stubsDir = new JBTextField();
        testMappings = new JBTextField();
        javaHome = new JBTextField();
        cliLib = new JBTextField();
        extraArgs = new JBTextField();
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Trial projects folder:", trialsRoot)
                .addComponentToRightColumn(hint("One trial project per class is created here. Original files are only read."))
                .addLabeledComponent("Stubs folder:", stubsDir)
                .addComponentToRightColumn(hint("Stand-in types shared by all classes. Empty: <trial>/src/stubs/gosu."))
                .addLabeledComponent("Test folder mappings:", testMappings)
                .addComponentToRightColumn(hint("source=test pairs separated by ';', e.g. src/main/gosu=src/test/gosu. "
                        + "Foo.gs is matched with FooTest.gs."))
                .addLabeledComponent("Java home:", javaHome)
                .addComponentToRightColumn(hint("JDK 17 or 21 that runs the tool. Empty: the IDE's own runtime."))
                .addLabeledComponent("Tool jars folder:", cliLib)
                .addComponentToRightColumn(hint("Only to use another gosu-mutation build. Empty: the bundled one."))
                .addLabeledComponent("Extra run options:", extraArgs)
                .addComponentToRightColumn(hint("For example: --threads 2 --timeout 120"))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    private static JBLabel hint(String text) {
        JBLabel label = new JBLabel(text);
        label.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        label.setFontColor(UIUtil.FontColor.BRIGHTER);
        return label;
    }

    @Override
    public boolean isModified() {
        GosuMutationSettings.State s = GosuMutationSettings.getInstance().getState();
        return !Objects.equals(trialsRoot.getText(), s.trialsRoot)
                || !Objects.equals(stubsDir.getText(), s.stubsDir)
                || !Objects.equals(testMappings.getText(), s.testMappings)
                || !Objects.equals(javaHome.getText(), s.javaHome)
                || !Objects.equals(cliLib.getText(), s.cliLib)
                || !Objects.equals(extraArgs.getText(), s.extraArgs);
    }

    @Override
    public void apply() {
        GosuMutationSettings.State s = GosuMutationSettings.getInstance().getState();
        s.trialsRoot = trialsRoot.getText().trim();
        s.stubsDir = stubsDir.getText().trim();
        s.testMappings = testMappings.getText().trim();
        s.javaHome = javaHome.getText().trim();
        s.cliLib = cliLib.getText().trim();
        s.extraArgs = extraArgs.getText().trim();
    }

    @Override
    public void reset() {
        GosuMutationSettings.State s = GosuMutationSettings.getInstance().getState();
        trialsRoot.setText(s.trialsRoot);
        stubsDir.setText(s.stubsDir);
        testMappings.setText(s.testMappings);
        javaHome.setText(s.javaHome);
        cliLib.setText(s.cliLib);
        extraArgs.setText(s.extraArgs);
    }
}
