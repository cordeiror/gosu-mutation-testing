package io.gosumutation.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

import java.nio.file.Path;

/** User settings, stored in gosu-mutation.xml of the IDE configuration. */
@Service(Service.Level.APP)
@State(name = "GosuMutationSettings", storages = @Storage("gosu-mutation.xml"))
public final class GosuMutationSettings implements PersistentStateComponent<GosuMutationSettings.State> {

    /** Serialized state. Public fields are required by the IntelliJ serializer. */
    public static final class State {
        /** One trial project per class is created under this folder. */
        public String trialsRoot = Path.of(System.getProperty("user.home"), "gosu-mutation-trials").toString();
        /** Shared folder with stand-in types; blank means {@code <trial>/src/stubs/gosu}. */
        public String stubsDir = "";
        /** Where tests live relative to sources, {@code from=to} separated by ';'. */
        public String testMappings = "src/main/gosu=src/test/gosu";
        /** JDK that runs the tool (17 or 21); blank means the IDE's own runtime. */
        public String javaHome = "";
        /** Folder with the jars of another gosu-mutation installation; blank means the bundled one. */
        public String cliLib = "";
        /** Extra options for "gosu-mutation run", e.g. "--threads 2 --timeout 120". */
        public String extraArgs = "";
        /** Tests chosen by the user, per class: source path -> test files and folders separated by '|'. */
        public java.util.Map<String, String> chosenTests = new java.util.TreeMap<>();
    }

    private State state = new State();

    public static GosuMutationSettings getInstance() {
        return ApplicationManager.getApplication().getService(GosuMutationSettings.class);
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(State loaded) {
        state = loaded;
    }
}
