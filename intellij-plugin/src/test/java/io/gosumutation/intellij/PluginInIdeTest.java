package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Starts a headless IntelliJ IDEA with the plugin loaded from its plugin.xml. */
public class PluginInIdeTest extends BasePlatformTestCase {

    private VirtualFile localFile(String name) throws Exception {
        Path dir = Files.createTempDirectory("gosu-plugin-test");
        Path file = Files.writeString(dir.resolve(name), "package a\nclass Foo {}\n");
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
        assertNotNull(virtualFile);
        return virtualFile;
    }

    private AnActionEvent eventFor(AnAction action, VirtualFile file) {
        return TestActionEvent.createTestEvent(action, SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.VIRTUAL_FILE, file)
                .build());
    }

    public void testActionsAreRegisteredInTheProjectViewAndEditorMenus() {
        ActionManager manager = ActionManager.getInstance();
        AnAction run = manager.getAction("GosuMutation.Run");
        AnAction request = manager.getAction("GosuMutation.BuildStubRequest");
        assertTrue(manager.getAction("GosuMutation.RunWithChosenTests") instanceof ChooseTestsAndRunAction);
        assertTrue(run instanceof RunMutationTestingAction);
        assertTrue(request instanceof BuildStubRequestAction);
        assertTrue(manager.getAction("GosuMutation.ShowResults") instanceof ShowMutationResultsAction);
        assertTrue(manager.getAction("GosuMutation.BuildTestRequest") instanceof TestRequestAction);
        for (String menu : new String[] {"ProjectViewPopupMenu", "EditorPopupMenu"}) {
            DefaultActionGroup group = (DefaultActionGroup) manager.getAction(menu);
            assertTrue(menu, Arrays.asList(group.getChildActionsOrStubs()).contains(manager.getAction("GosuMutation.Group")));
        }
    }

    public void testRunIsOfferedOnlyForGosuClasses() throws Exception {
        AnAction run = ActionManager.getInstance().getAction("GosuMutation.Run");

        AnActionEvent onGosu = eventFor(run, localFile("Foo.gs"));
        run.update(onGosu);
        assertTrue(onGosu.getPresentation().isEnabledAndVisible());

        AnActionEvent onJava = eventFor(run, localFile("Foo.java"));
        run.update(onJava);
        assertFalse(onJava.getPresentation().isVisible());
    }

    public void testStubRequestIsAlwaysEnabledOnGosuClassesSoItCanExplainItself() throws Exception {
        AnAction request = ActionManager.getInstance().getAction("GosuMutation.BuildStubRequest");
        GosuMutationSettings.getInstance().getState().trialsRoot = Files.createTempDirectory("trials").toString();

        AnActionEvent onGosu = eventFor(request, localFile("Foo.gs"));
        request.update(onGosu);
        assertTrue(onGosu.getPresentation().isEnabledAndVisible());

        AnActionEvent onJava = eventFor(request, localFile("Foo.java"));
        request.update(onJava);
        assertFalse(onJava.getPresentation().isVisible());
    }

    public void testResultsAreLoadedIntoTheToolWindowAndTheEditor() throws Exception {
        Path trials = Files.createTempDirectory("trials");
        GosuMutationSettings.getInstance().getState().trialsRoot = trials.toString();
        Path dir = Files.createTempDirectory("code");
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), dir.toRealPath().toString());
        String code = "package demo\n\nclass Foo {\n  function f() {\n    if (a == b) {\n    }\n"
                + "    var y = 1\n    var z = 2\n    if (x and not y) {\n    }\n    var w = 3\n    return \"B\"\n  }\n}\n";
        Path source = Files.writeString(dir.resolve("Foo.gs"), code);
        TrialPaths paths = TrialPaths.of(source, trials.toString(), "");
        Files.createDirectories(paths.reportDir());
        Files.writeString(paths.jsonReport(), ResultsLogicTest.REPORT);
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source);
        myFixture.openFileInEditor(file);

        MutationResultsService service = MutationResultsService.getInstance(getProject());
        MutationResultsService.Loaded loaded = service.load(source);
        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals(3, loaded.results().mutants().size());
        assertSame(loaded, service.current());
        MutationResultsPanel panel = new MutationResultsPanel(getProject(), getTestRootDisposable());
        assertNotNull(panel.getContent());
        var markup = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(myFixture.getEditor().getDocument(), getProject(), true);
        java.util.List<Integer> markedLines = new java.util.ArrayList<>();
        for (var highlighter : markup.getAllHighlighters()) {
            if (highlighter.getGutterIconRenderer() instanceof com.intellij.openapi.editor.markup.GutterIconRenderer renderer
                    && renderer.getTooltipText() != null && renderer.getTooltipText().contains("Mutation testing")) {
                markedLines.add(myFixture.getEditor().getDocument().getLineNumber(highlighter.getStartOffset()) + 1);
            }
        }
        java.util.Collections.sort(markedLines);
        // Line 12 was edited since the run ("B" instead of "A"), so it gets no marker.
        assertEquals(java.util.List.of(5, 9), markedLines);

        service.setEquivalent(loaded, loaded.results().mutants().get(1), true);
        com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals(MutationResults.EQUIVALENT, service.current().results().mutants().get(1).status());
        assertTrue(EquivalentMutantsFile.read(paths.equivalentFile()).contains("bbb222"));
    }

    public void testSettingsDefaultsAndSettingsPage() {
        GosuMutationSettings.State state = new GosuMutationSettings.State();
        assertEquals("src/main/gosu=src/test/gosu", state.testMappings);
        assertTrue(state.trialsRoot.endsWith("gosu-mutation-trials"));
        assertTrue(state.chosenTests.isEmpty());
        assertFalse(state.openHtmlReport);

        GosuMutationConfigurable page = new GosuMutationConfigurable();
        assertNotNull(page.createComponent());
        assertFalse(page.isModified());
    }

    public void testTheBundledJarsAreFoundThroughThePluginDescriptor() {
        // In tests the plugin is loaded from the build output, so only the descriptor lookup is checked here;
        // the jars themselves are checked by BundledCliIntegrationTest and the plugin zip.
        GosuMutationSettings.State state = new GosuMutationSettings.State();
        Path lib = MutationRunner.cliLib(state);
        assertNotNull("plugin " + MutationRunner.PLUGIN_ID + " must be loaded", lib);
        assertTrue(lib.toString(), lib.endsWith(Path.of("cli", "lib")));
    }
}
