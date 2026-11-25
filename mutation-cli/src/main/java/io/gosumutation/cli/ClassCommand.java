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
 *
 * <p>The trial project is marked with {@value #MARKER} when this command creates it. In a marked
 * trial project, {@code src/main/gosu} (and {@code src/test/gosu} when tests are given) are emptied
 * before copying, so files from an earlier run cannot interfere. Folders the command did not create
 * are never emptied.
 */
@Command(name = "class", mixinStandardHelpOptions = true,
        description = {"Copies one Gosu class (and optionally its tests) into a trial project and mutation tests it.",
                "Any option of 'run' that is not listed here (e.g. --threads, --timeout) is passed through to it."})
final class ClassCommand implements Callable<Integer> {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)");
    static final String MARKER = ".gosu-mutation-trial";

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"-s", "--source"}, required = true, paramLabel = "<file.gs>", description = "The Gosu class to mutate.")
    Path source;

    @Option(names = {"-d", "--trial-dir"}, required = true, paramLabel = "<dir>",
            description = "Trial project that receives the copies. Created if missing.")
    Path trialDir;

    @Option(names = "--test", paramLabel = "<file.gs|dir>",
            description = "A test class, or a folder whose tree is searched for Gosu files (.gs, .gsx), to copy into "
                    + "<trial-dir>/src/test/gosu by package. Repeatable. "
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
        List<TestInput> testInputs = new ArrayList<>();
        for (Path test : tests) {
            testInputs.add(expand(test.toAbsolutePath().normalize()));
        }
        Path trial = trialDir.toAbsolutePath().normalize();
        Path testDir = trial.resolve("src/test/gosu");
        Path stubs = stubsDir != null ? stubsDir.toAbsolutePath().normalize() : trial.resolve("src/stubs/gosu");
        if (stubsDir != null && !Files.isDirectory(stubs)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "--stubs-dir is not a directory: " + stubs);
        }

        try {
            boolean ownTrial = Files.isRegularFile(trial.resolve(MARKER));
            boolean newTrial = !Files.exists(trial) || isEmptyDirectory(trial);
            if (!ownTrial && !newTrial) {
                out.println("Note: " + trial + " was not created by this command, so files already in it are kept.");
            }
            Files.createDirectories(trial);
            if (newTrial) {
                Files.writeString(trial.resolve(MARKER), "Trial project created by 'gosu-mutation class'. "
                        + "src/main/gosu and src/test/gosu are replaced on every run." + System.lineSeparator());
            }
            if (ownTrial) {
                deleteTree(trial.resolve("src/main/gosu"));
                if (!testInputs.isEmpty()) {
                    deleteTree(testDir);
                }
            }
            for (TestInput input : testInputs) {
                for (int i = 0; i < input.files().size(); i++) {
                    copyInto(input.files().get(i), testDir, input.packages().get(i), input.isFolder() ? null : out);
                }
                if (input.isFolder()) {
                    out.println("Copied " + input.files().size() + " Gosu file(s) found under " + input.path() + " to " + testDir);
                }
            }
            if (!hasGosuFiles(testDir)) {
                err.println("ERROR: no Gosu tests found in " + testDir + ".");
                // Plain text on purpose: '<' and '>' are not valid in Windows paths.
                String separator = testDir.getFileSystem().getSeparator();
                err.println("Pass them with --test <file.gs or folder> or put them in "
                        + testDir + separator + "<package>" + separator + "<Class>Test.gs.");
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

    /** A --test value: one file, or every Gosu file found under a folder. */
    private record TestInput(Path path, boolean isFolder, List<Path> files, List<String> packages) { }

    private TestInput expand(Path path) {
        List<Path> files;
        boolean folder = Files.isDirectory(path);
        if (folder) {
            try (Stream<Path> tree = Files.walk(path)) {
                files = tree.filter(Files::isRegularFile).filter(ClassCommand::isGosuSource).sorted().toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (files.isEmpty()) {
                throw new CommandLine.ParameterException(spec.commandLine(), "--test folder has no Gosu files: " + path);
            }
        } else {
            files = List.of(path);
        }
        List<String> packages = new ArrayList<>();
        for (Path file : files) {
            packages.add(packageOf(file, "--test"));
        }
        return new TestInput(path, folder, files, packages);
    }

    private static boolean isEmptyDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> children = Files.list(dir)) {
            return children.findAny().isEmpty();
        }
    }

    private static boolean isGosuSource(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".gs") || name.endsWith(".gsx");
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(dir)) {
            for (Path path : tree.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private String packageOf(Path file, String option) {
        if (!Files.isRegularFile(file) || !isGosuSource(file)) {
            throw new CommandLine.ParameterException(spec.commandLine(), option + " is not a Gosu file or folder: " + file);
        }
        Matcher matcher = PACKAGE.matcher(read(file));
        if (!matcher.find()) {
            throw new CommandLine.ParameterException(spec.commandLine(), "No package declaration found in " + file);
        }
        return matcher.group(1);
    }

    /** @param out where to log the copy, or null to stay quiet */
    private static void copyInto(Path file, Path root, String packageName, PrintWriter out) throws IOException {
        Path targetDir = root.resolve(packageName.replace('.', '/'));
        Files.createDirectories(targetDir);
        Files.copy(file, targetDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        if (out != null) {
            out.println("Copied " + file + " to " + targetDir);
        }
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
