package io.gosumutation.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The mutation results of the classes of a project: loaded from each class's trial report, shown
 * in the Gosu Mutation tool window and as markers in the editor.
 */
@Service(Service.Level.PROJECT)
public final class MutationResultsService implements Disposable {

    static final String TOOL_WINDOW_ID = "Gosu Mutation";

    /** Results of one class and where its trial is. */
    record Loaded(Path source, TrialPaths paths, MutationResults results) { }

    interface Listener {
        void resultsChanged(Loaded loaded);
    }

    private final Project project;
    private final Map<Path, Loaded> bySource = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final EditorMarkers markers;
    private volatile Loaded current;

    public MutationResultsService(Project project) {
        this.project = project;
        this.markers = new EditorMarkers(project);
        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void fileOpened(FileEditorManager manager, VirtualFile file) {
                        Loaded loaded = bySource.get(GosuFiles.pathOf(file));
                        if (loaded != null) {
                            markers.show(loaded);
                        }
                    }
                });
    }

    static MutationResultsService getInstance(Project project) {
        return project.getService(MutationResultsService.class);
    }

    Loaded current() {
        return current;
    }

    void addListener(Listener listener, Disposable parent) {
        listeners.add(listener);
        com.intellij.openapi.util.Disposer.register(parent, () -> listeners.remove(listener));
    }

    /**
     * Reads the last report of the class and shows it.
     *
     * @return the results, or null when the class has no report yet
     */
    Loaded load(Path source) throws IOException {
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
        TrialPaths paths = TrialPaths.of(source, settings.trialsRoot, settings.stubsDir);
        if (!Files.isRegularFile(paths.jsonReport())) {
            return null;
        }
        MutationResults results = MutationResults.read(paths.jsonReport())
                .withEquivalent(EquivalentMutantsFile.read(paths.equivalentFile()));
        Loaded loaded = new Loaded(source, paths, results);
        publish(loaded);
        return loaded;
    }

    /** Marks or unmarks a mutant as equivalent; takes effect in the view now and in the next run. */
    void setEquivalent(Loaded loaded, MutationResults.Mutant mutant, boolean equivalent) throws IOException {
        if (equivalent) {
            EquivalentMutantsFile.add(loaded.paths().equivalentFile(), mutant);
        } else {
            EquivalentMutantsFile.remove(loaded.paths().equivalentFile(), mutant.id());
        }
        MutationResults updated = loaded.results().withEquivalent(EquivalentMutantsFile.read(loaded.paths().equivalentFile()));
        publish(new Loaded(loaded.source(), loaded.paths(), updated));
    }

    private void publish(Loaded loaded) {
        bySource.put(loaded.source(), loaded);
        current = loaded;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            markers.show(loaded);
            listeners.forEach(listener -> listener.resultsChanged(loaded));
        });
    }

    /** Opens the tool window; {@code line} (1-based, or 0) selects the first mutant of that line. */
    void showToolWindow(int line) {
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (window != null) {
            window.activate(() -> {
                if (line > 0) {
                    listeners.forEach(listener -> {
                        if (listener instanceof MutationResultsPanel panel) {
                            panel.selectLine(line);
                        }
                    });
                }
            });
        }
    }

    static Document documentOf(Path source) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByNioFile(source);
        return file == null ? null : FileDocumentManager.getInstance().getDocument(file);
    }

    @Override
    public void dispose() {
        listeners.clear();
        markers.clear();
    }
}
