package io.gosumutation.intellij;

import java.nio.file.Path;
import java.util.List;

/** The texts of the stubs wizard, kept apart from the dialog so they can be tested without the IDE. */
final class StubWizardTexts {

    /** At most this many missing names are listed per group; the AI request has all of them. */
    static final int MAX_LISTED = 40;

    private StubWizardTexts() { }

    static String intro(String className) {
        return page("""
                <p><b>Mutation testing runs %1$s and its tests outside your project</b>, in a separate
                <i>trial project</i>. Your original files are only read, never changed.</p>

                <p><b>Why it may not compile there.</b> Classes of a large code base use other types of that code
                base: entities, typekeys, utilities, loggers, test base classes. The trial project only has
                %1$s and its tests, so the Gosu compiler cannot find those types.</p>

                <p><b>Stubs solve that.</b> A stub is a minimal stand-in for a missing type: the same package and
                name, with only the members the class and its tests use, and simple behavior. For example a
                typekey becomes a small enum, and a utility function returns what the tests need. Stubs live
                in a folder of their own, outside your project and outside version control.</p>

                <p><b>How they are created.</b> The plugin prepares a request with the compiler errors, the class,
                its tests and a guide; an AI assistant that can write files (GitHub Copilot Chat in agent mode,
                for example) creates the stubs. You can also write them by hand with the stubbing guide.</p>

                <p><b>The loop.</b> Check, create stubs, check again. Each check runs only the compiler and the
                tests, in a few seconds, and shows how many errors are left. When the class compiles and its
                tests pass, mutation testing can start.</p>

                <p>Stubs only have to make the class compile and the tests behave as in the real code base.
                The mutants are made in %1$s itself, never in the stubs.</p>
                """.formatted(escape(className)));
    }

    static String setup() {
        return page("""
                <p>Check the tests of the class and where the stubs go. The tests can be files or folders
                (folders are searched recursively). The choice is remembered for this class.</p>
                """);
    }

    static String beforeCheck(String className) {
        return page("<p>Press <b>Check Now</b> to compile " + escape(className)
                + " and its tests in the trial project and run the tests.</p>");
    }

    static String checking(String className) {
        return page("<p>Compiling " + escape(className) + " and its tests and running the tests in the trial project...</p>");
    }

    static String problem(String text) {
        return page("<p><b>Problem:</b> " + escape(text) + "</p>");
    }

    static String result(String className, StubCheck check, List<Integer> history, Path stubsDir) {
        StringBuilder html = new StringBuilder();
        switch (check.kind()) {
            case READY -> html.append("<p><b>Ready.</b> ").append(escape(className))
                    .append(" compiles and its tests pass in the trial project. Press <b>Run Mutation Testing</b>.</p>");
            case DOES_NOT_COMPILE -> {
                html.append("<p><b>").append(escape(className)).append(" does not compile yet");
                if (check.errorCount() > 0) {
                    html.append(": ").append(check.errorCount()).append(check.errorCount() == 1 ? " error" : " errors");
                }
                html.append(".</b></p>");
                appendMissing(html, check.missing());
                html.append("<p><b>Next:</b></p><ol>")
                    .append("<li><b>Copy AI Stub Request</b> and paste it into Copilot Chat in agent mode "
                            + "(or write the stubs by hand with the stubbing guide).</li>")
                    .append("<li>Let it create the files in <code>").append(escape(stubsDir.toString())).append("</code>.</li>")
                    .append("<li>Press <b>Check Now</b> again. Repeat until the class compiles: new errors can appear ")
                    .append("as stubs are added, because the compiler gets further.</li></ol>");
            }
            case TESTS_FAIL -> html.append("<p><b>").append(escape(className))
                    .append(" compiles, but the tests do not pass in the trial project.</b> A stub probably returns ")
                    .append("something different from the real type. <b>Copy AI Stub Request</b> asks the assistant to ")
                    .append("adjust the stubs; then press <b>Check Now</b>.</p><pre>")
                    .append(escape(firstLines(check.message(), 25))).append("</pre>");
            case OTHER -> html.append("<p><b>The check stopped before compiling the class.</b> ")
                    .append("Often the tests were not found: go <b>Back</b> and choose them.</p><pre>")
                    .append(escape(check.message())).append("</pre>");
        }
        if (history.size() > 1) {
            html.append("<p>Errors per check: ")
                .append(String.join(" &rarr; ", history.stream().map(String::valueOf).toList())).append("</p>");
        }
        return page(html.toString());
    }

    private static void appendMissing(StringBuilder html, BaselineErrors.Missing missing) {
        if (missing.isEmpty()) {
            html.append("<p>The errors do not name a missing type; see the request for the full compiler output.</p>");
            return;
        }
        if (!missing.types().isEmpty()) {
            html.append("<p>Types to create as stubs (").append(missing.types().size()).append("):</p>");
            list(html, missing.types());
        }
        if (!missing.symbols().isEmpty()) {
            html.append("<p>Other names not found (").append(missing.symbols().size())
                .append("): usually members of a missing type, a typekey, or a function a stub must provide:</p>");
            list(html, missing.symbols());
        }
        html.append("<p>Errors about local variables and block parameters of the class are left out: they "
                + "disappear once the types above exist.</p>");
    }

    private static void list(StringBuilder html, List<String> names) {
        html.append("<p><code>");
        List<String> shown = names.subList(0, Math.min(MAX_LISTED, names.size()));
        html.append(String.join("</code>, <code>", shown.stream().map(StubWizardTexts::escape).toList()));
        html.append("</code>");
        if (names.size() > MAX_LISTED) {
            html.append(" and ").append(names.size() - MAX_LISTED).append(" more (all are in the request)");
        }
        html.append("</p>");
    }

    private static String firstLines(String text, int count) {
        List<String> lines = text.lines().toList();
        return String.join("\n", lines.subList(0, Math.min(count, lines.size())))
                + (lines.size() > count ? "\n..." : "");
    }

    private static String page(String body) {
        return "<html><body>" + body + "</body></html>";
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
