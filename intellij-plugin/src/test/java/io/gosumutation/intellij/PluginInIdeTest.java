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
        assertTrue(run instanceof RunMutationTestingAction);
        assertTrue(request instanceof BuildStubRequestAction);
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

    public void testStubRequestIsDisabledUntilARunFailed() throws Exception {
        AnAction request = ActionManager.getInstance().getAction("GosuMutation.BuildStubRequest");
        GosuMutationSettings.getInstance().getState().trialsRoot = Files.createTempDirectory("trials").toString();

        AnActionEvent event = eventFor(request, localFile("Foo.gs"));
        request.update(event);

        assertTrue(event.getPresentation().isVisible());
        assertFalse(event.getPresentation().isEnabled());
    }

    public void testSettingsDefaultsAndSettingsPage() {
        GosuMutationSettings.State state = new GosuMutationSettings.State();
        assertEquals("src/main/gosu=src/test/gosu", state.testMappings);
        assertTrue(state.trialsRoot.endsWith("gosu-mutation-trials"));

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
