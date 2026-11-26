package io.gosumutation.intellij;

/** What each menu option does, shown by "How Do These Options Work?". Context menus show no tooltips. */
final class PluginHelp {

    private PluginHelp() { }

    static String html() {
        return """
                <html><body>
                <p>Mutation testing changes the class one small piece at a time (a <i>mutant</i>: <code>&gt;=</code>
                becomes <code>&gt;</code>, <code>and</code> becomes <code>or</code>...) and runs its tests. A test
                suite is good when the tests fail for every mutant. Everything runs in a separate <i>trial
                project</i>; your files are never changed.</p>

                <p><b>Run Mutation Testing</b><br>
                Runs the class with its tests (found through the test folder mappings, or the ones you chose).
                The first time, it offers the wizard below. Results show in the <b>Gosu Mutation</b> tool window
                and as icons in the editor margin.</p>

                <p><b>Run Mutation Testing with Chosen Tests...</b><br>
                Choose the test files and/or folders of the class (remembered), then run.</p>

                <p><b>Prepare Class (Stubs Wizard)...</b><br>
                For classes from a large code base that do not compile on their own. Explains what stubs are,
                checks the class in seconds, prepares the request for an AI assistant, and repeats until the
                class compiles and its tests pass.</p>

                <p><b>Show Mutation Results</b><br>
                Shows the last results of the class without running again.</p>

                <p><b>Build AI Test Request</b><br>
                Asks an AI assistant for tests that kill the mutants that survived (or for an explanation when
                a mutant cannot be killed: then use <b>Mark as Equivalent</b> in the tool window).</p>

                <p><b>Build AI Stub Request</b><br>
                Asks an AI assistant to create the stubs for the compiler errors of the last run, without the
                wizard.</p>

                <p><b>Results</b>: <i>Killed</i> a test failed (good); <i>Survived</i> no test noticed the change;
                <i>No coverage</i> no test runs that line; <i>Equivalent</i> marked as impossible to detect.
                Score = killed / (killed + survived).</p>

                <p>Settings: <b>Settings | Tools | Gosu Mutation Testing</b>.</p>
                </body></html>
                """;
    }
}
