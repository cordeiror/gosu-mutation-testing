package io.gosumutation.intellij;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the command line that runs {@code gosu-mutation class} from the bundled jars. */
final class MutationCommand {

    static final String MAIN_CLASS = "io.gosumutation.cli.Main";
    private static final Pattern ARGUMENT = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    private MutationCommand() { }

    /**
     * @param javaHome  JDK or JRE that runs the tool (Java 17 or 21)
     * @param cliLib    folder with the jars of the command line tool
     * @param source    class to mutate
     * @param test      its test, or null when the tests are already in the trial project
     * @param trialDir  trial project
     * @param stubsDir  stand-in types, or null for the default ({@code <trialDir>/src/stubs/gosu})
     * @param extraArgs other options of {@code gosu-mutation run}, as typed by the user
     */
    record Request(Path javaHome, Path cliLib, Path source, Path test, Path trialDir, Path stubsDir, String extraArgs) { }

    static List<String> build(Request request) {
        boolean windows = File.separatorChar == '\\';
        List<String> command = new ArrayList<>();
        command.add(request.javaHome().resolve("bin").resolve(windows ? "java.exe" : "java").toString());
        command.add("-cp");
        command.add(request.cliLib().toString() + File.separator + "*");
        command.add(MAIN_CLASS);
        command.add("class");
        command.add("--source");
        command.add(request.source().toString());
        command.add("--trial-dir");
        command.add(request.trialDir().toString());
        if (request.test() != null) {
            command.add("--test");
            command.add(request.test().toString());
        }
        if (request.stubsDir() != null) {
            command.add("--stubs-dir");
            command.add(request.stubsDir().toString());
        }
        command.addAll(splitArguments(request.extraArgs()));
        return command;
    }

    /** Splits on spaces, keeping "quoted values" together. */
    static List<String> splitArguments(String text) {
        List<String> arguments = new ArrayList<>();
        if (text == null) {
            return arguments;
        }
        Matcher matcher = ARGUMENT.matcher(text);
        while (matcher.find()) {
            arguments.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        return arguments;
    }
}
