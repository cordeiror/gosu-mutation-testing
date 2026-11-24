package io.gosumutation.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mutation tests one Gosu class taken from another code base: copies the class (and optionally its
 * tests) into a separate trial project, by package, then runs {@code gosu-mutation run} on that
 * project mutating only the class. Original files are only read.
 */
@Command(name = "class", mixinStandardHelpOptions = true,
        description = {"Copies one Gosu class (and optionally its tests) into a trial project and mutation tests it.",
                "Any option of 'run' that is not listed here (e.g. --threads, --timeout) is passed through to it."})
final class ClassCommand implements Callable<Integer> {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)");

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"-s", "--source"}, required = true, paramLabel = "<file.gs>", description = "The Gosu class to mutate.")
    Path source;

    @Option(names = {"-d", "--trial-dir"}, required = true, paramLabel = "<dir>",
            description = "Trial project that receives the copies. Created if missing.")
    Path trialDir;

    @Option(names = "--test", paramLabel = "<file.gs>",
            description = "A test class to copy into <trial-dir>/src/test/gosu. Repeatable. "
                    + "Without it the tests must already be in <trial-dir>/src/test/gosu.")
    List<Path> tests = new ArrayList<>();

    @Option(names = "--stubs-dir", paramLabel = "<dir>",
            description = "Stand-in types needed by the class or its tests (compiled, never mutated). "
                    + "Default: <trial-dir>/src/stubs/gosu when it exists.")
    Path stubsDir;

    @CommandLine.Unmatched
    List<String> runOptions = new ArrayList<>();

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        if (runOptions.stream().anyMatch(o -> o.equals("-p") || o.equals("--project") || o.startsWith("--project=")
                || o.equals("-c") || o.equals("--class") || o.startsWith("--class="))) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "--project and --class are set by this command; use --trial-dir and --source");
        }

        // Validate every input before touching the trial project.
        Path sourceFile = source.toAbsolutePath().normalize();
        String sourcePackage = packageOf(sourceFile, "--source");
        String className = sourcePackage + "." + stripExtension(sourceFile.getFileName().toString());
        List<Path> testFiles = tests.stream().map(p -> p.toAbsolutePath().normalize()).toList();
        List<String> testPackages = new ArrayList<>();
        for (Path test : testFiles) {
            testPackages.add(packageOf(test, "--test"));
        }
        Path trial = trialDir.toAbsolutePath().normalize();
        Path testDir = trial.resolve("src/test/gosu");
        Path stubs = stubsDir != null ? stubsDir.toAbsolutePath().normalize() : trial.resolve("src/stubs/gosu");
        if (stubsDir != null && !Files.isDirectory(stubs)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "--stubs-dir is not a directory: " + stubs);
        }

        try {
            for (int i = 0; i < testFiles.size(); i++) {
                copyInto(testFiles.get(i), testDir, testPackages.get(i), out);
            }
            if (!hasGosuFiles(testDir)) {
                err.println("ERROR: no Gosu tests found in " + testDir + ".");
                err.println("Pass them with --test <file.gs> or put them in " + testDir.resolve("<package>").resolve("<Class>Test.gs") + ".");
                nestedTestDir(trial).ifPresent(nested -> err.println("Found src/test/gosu one level deeper, in " + nested
                        + ": move its contents up to " + trial + " or use that folder as --trial-dir."));
                return ExitCodes.FAILURE;
            }
            copyInto(sourceFile, trial.resolve("src/main/gosu"), sourcePackage, out);
        } catch (IOException e) {
            err.println("ERROR: could not prepare the trial project: " + e);
            return ExitCodes.FAILURE;
        }

        List<String> args = new ArrayList<>(List.of("run", "--project", trial.toString(), "--class", className,
                "--test-dir", "src/test/gosu"));
        if (Files.isDirectory(stubs)) {
            args.add("--test-dir");
            args.add(stubs.toString());
            out.println("Using stand-in types from " + stubs);
        } else {
            out.println("No stubs directory: every type the class uses must come from Gosu, the JDK or --classpath");
        }
        args.addAll(runOptions);
        out.flush();

        CommandLine root = spec.commandLine().getParent();
        return root.execute(args.toArray(String[]::new));
    }

    private String packageOf(Path file, String option) {
        String name = file.getFileName().toString();
        if (!Files.isRegularFile(file) || !name.endsWith(".gs")) {
            throw new CommandLine.ParameterException(spec.commandLine(), option + " is not a Gosu class file: " + file);
        }
        Matcher matcher = PACKAGE.matcher(read(file));
        if (!matcher.find()) {
            throw new CommandLine.ParameterException(spec.commandLine(), "No package declaration found in " + file);
        }
        return matcher.group(1);
    }

    private static void copyInto(Path file, Path root, String packageName, PrintWriter out) throws IOException {
        Path targetDir = root.resolve(packageName.replace('.', '/'));
        Files.createDirectories(targetDir);
        Files.copy(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        out.println("Copied " + file + " to " + targetDir);
    }

    private static boolean hasGosuFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.anyMatch(p -> p.toString().endsWith(".gs"));
        }
    }

    private static java.util.Optional<Path> nestedTestDir(Path trial) throws IOException {
        if (!Files.isDirectory(trial)) {
            return java.util.Optional.empty();
        }
        try (Stream<Path> children = Files.list(trial)) {
            return children.filter(child -> Files.isDirectory(child.resolve("src/test/gosu"))).findFirst();
        }
    }

    private static String stripExtension(String fileName) {
        return fileName.substring(0, fileName.length() - ".gs".length());
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
