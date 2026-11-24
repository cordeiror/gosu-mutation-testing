package io.gosumutation.core.process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Builds command lines that start a child JVM with the same Java installation as the framework. */
public final class ForkedJvm {

    private ForkedJvm() { }

    /** Class path of the running framework: Gosu, the test worker and the bundled JUnit engines. */
    public static List<Path> frameworkClasspath() {
        List<Path> entries = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank() && Files.exists(Path.of(entry))) {
                entries.add(Path.of(entry).toAbsolutePath());
            }
        }
        return entries;
    }

    /**
     * The class path is passed through a Java argument file, which keeps the command line short
     * enough for Windows no matter how many dependencies the project has.
     */
    public static List<String> command(Path workDir, List<String> jvmArgs, List<Path> classpath, String mainClass,
                                       List<String> programArgs) throws IOException {
        Files.createDirectories(workDir);
        Path argFile = Files.createTempFile(workDir, "jvm-", ".args");
        String joined = classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        Files.writeString(argFile, "-cp\n\"" + joined.replace("\\", "\\\\") + "\"\n");

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvmArgs);
        command.add("@" + argFile);
        command.add(mainClass);
        command.addAll(programArgs);
        return command;
    }
}
