package io.gosumutation.intellij;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;

import javax.swing.Icon;
import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Gutter markers on the lines of a class that have mutants: a warning where the tests miss a
 * mutant, a check where they kill all of them. Lines edited since the run are skipped, because
 * their mutants no longer match the code.
 */
final class EditorMarkers {

    private static final Color UNDETECTED_STRIPE = new JBColor(new Color(0xE0, 0x6C, 0x00), new Color(0xF0, 0xA7, 0x32));

    private final Project project;
    private final Map<Path, List<RangeHighlighter>> highlighters = new HashMap<>();

    EditorMarkers(Project project) {
        this.project = project;
    }

    void show(MutationResultsService.Loaded loaded) {
        remove(loaded.source());
        Document document = MutationResultsService.documentOf(loaded.source());
        if (document == null) {
            return;
        }
        Map<Integer, List<MutationResults.Mutant>> byLine = new LinkedHashMap<>();
        for (MutationResults.Mutant mutant : loaded.results().mutants()) {
            if (matches(document, mutant)) {
                byLine.computeIfAbsent(mutant.line(), line -> new ArrayList<>()).add(mutant);
            }
        }
        MarkupModel markup = DocumentMarkupModel.forDocument(document, project, true);
        List<RangeHighlighter> added = new ArrayList<>();
        byLine.forEach((line, mutants) -> {
            boolean undetected = mutants.stream().anyMatch(MutationResults.Mutant::undetected);
            boolean allKilled = mutants.stream().allMatch(m -> MutationResults.KILLED.equals(m.status()));
            if (!undetected && !allKilled) {
                return;
            }
            RangeHighlighter highlighter = markup.addLineHighlighter(line - 1, HighlighterLayer.WARNING, null);
            highlighter.setGutterIconRenderer(new Renderer(line, undetected, tooltip(mutants)));
            if (undetected) {
                highlighter.setErrorStripeMarkColor(UNDETECTED_STRIPE);
                highlighter.setErrorStripeTooltip(tooltip(mutants));
            }
            added.add(highlighter);
        });
        highlighters.put(loaded.source(), added);
    }

    /** The line still holds the code that was mutated. */
    static boolean matches(Document document, MutationResults.Mutant mutant) {
        int index = mutant.line() - 1;
        if (index < 0 || index >= document.getLineCount()) {
            return false;
        }
        String text = document.getText(new com.intellij.openapi.util.TextRange(
                document.getLineStartOffset(index), document.getLineEndOffset(index)));
        return text.strip().equals(mutant.originalLine());
    }

    static String tooltip(List<MutationResults.Mutant> mutants) {
        StringBuilder html = new StringBuilder("<html><b>Mutation testing</b>");
        for (MutationResults.Mutant mutant : mutants) {
            html.append("<br>").append(MutationResults.label(mutant.status())).append(": ")
                .append(com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(mutant.description()));
        }
        return html.append("</html>").toString();
    }

    private void remove(Path source) {
        List<RangeHighlighter> old = highlighters.remove(source);
        if (old != null) {
            old.stream().filter(RangeHighlighter::isValid).forEach(RangeHighlighter::dispose);
        }
    }

    void clear() {
        new ArrayList<>(highlighters.keySet()).forEach(this::remove);
    }

    private final class Renderer extends GutterIconRenderer {
        private final int line;
        private final boolean undetected;
        private final String tooltip;

        Renderer(int line, boolean undetected, String tooltip) {
            this.line = line;
            this.undetected = undetected;
            this.tooltip = tooltip;
        }

        @Override
        public Icon getIcon() {
            return undetected ? AllIcons.General.Warning : AllIcons.RunConfigurations.TestPassed;
        }

        @Override
        public String getTooltipText() {
            return tooltip;
        }

        @Override
        public boolean isNavigateAction() {
            return true;
        }

        @Override
        public AnAction getClickAction() {
            return new AnAction() {
                @Override
                public void actionPerformed(AnActionEvent event) {
                    MutationResultsService.getInstance(project).showToolWindow(line);
                }
            };
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Renderer r && r.line == line && r.undetected == undetected && r.tooltip.equals(tooltip);
        }

        @Override
        public int hashCode() {
            return Objects.hash(line, undetected, tooltip);
        }
    }
}
