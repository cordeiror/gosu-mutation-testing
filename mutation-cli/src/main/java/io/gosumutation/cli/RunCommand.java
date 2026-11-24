package io.gosumutation.cli;

import io.gosumutation.core.BaselineFailedException;
import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.MutationEngine;
import io.gosumutation.core.MutationRunReport;
import io.gosumutation.core.MutationScore;
import io.gosumutation.core.port.MutationListener;
import io.gosumutation.core.port.ProjectCompiler;
import io.gosumutation.core.port.ReportGenerator;
import io.gosumutation.core.port.TestRunner;
import io.gosumutation.gosu.GosuCompilerAdapter;
import io.gosumutation.gosu.GosuMutationGenerator;
import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuOperators;
import io.gosumutation.report.HtmlReportGenerator;
import io.gosumutation.report.JsonReportGenerator;
import io.gosumutation.runner.GosuTestRunner;
import io.gosumutation.runner.IsolatedBaselineRunner;
import io.gosumutation.runner.MutationExecutor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "run", mixinStandardHelpOptions = true,
        description = "Runs mutation testing on a Gosu project and writes the reports.")
final class RunCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"-p", "--project"}, paramLabel = "<dir>", defaultValue = ".",
            description = "Project root (default: current directory).")
    Path project;

    @Option(names = "--source-dir", paramLabel = "<dir>",
            description = "Production source directory, relative to the project. Repeatable (default: src/main/gosu).")
    List<Path> sourceDirs = new ArrayList<>();

    @Option(names = "--test-dir", paramLabel = "<dir>",
            description = "Test source directory, relative to the project. Repeatable (default: src/test/gosu).")
    List<Path> testDirs = new ArrayList<>();

    @Option(names = {"-cp", "--classpath"}, paramLabel = "<path>", split = "[:;](?![\\\\/])",
            description = "Extra jars/directories needed to compile and run the project, separated by the path separator. "
                    + "Gosu, JUnit 5 and JUnit 4 are already provided.")
    List<Path> classpath = new ArrayList<>();

    @Option(names = {"-c", "--class"}, paramLabel = "<name>", split = ",",
            description = "Only mutate these classes: simple or fully qualified names, '*' allowed. Repeatable.")
    List<String> classes = new ArrayList<>();

    @Option(names = {"-o", "--operators"}, paramLabel = "<id>", split = ",",
            description = "Only use these mutation operators (see 'gosu-mutation operators'). Default: all.")
    List<String> operators = new ArrayList<>();

    @Option(names = {"-t", "--timeout"}, paramLabel = "<seconds>", defaultValue = "60",
            description = "Time limit to compile and to test each mutant (default: ${DEFAULT-VALUE}).")
    long timeoutSeconds;

    @Option(names = {"-j", "--threads"}, paramLabel = "<n>",
            description = "Mutants evaluated in parallel (default: half of the available processors, at most 4).")
    Integer threads;

    @Option(names = {"-r", "--report-dir"}, paramLabel = "<dir>",
            description = "Where reports are written (default: <project>/build/reports/gosu-mutation).")
    Path reportDir;

    @Option(names = {"-f", "--format"}, paramLabel = "<format>", split = ",", defaultValue = "html,json",
            description = "Report formats: html, json (default: ${DEFAULT-VALUE}).")
    List<String> formats;

    @Option(names = "--threshold", paramLabel = "<percent>",
            description = "Exit with code 3 when the mutation score is below this value.")
    Double threshold;

    @Option(names = {"-q", "--quiet"}, description = "Do not log progress.")
    boolean quiet;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        Path projectDir = project.toAbsolutePath().normalize();
        if (!Files.isDirectory(projectDir)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Project directory does not exist: " + projectDir);
        }
        List<Path> sources = resolve(projectDir, sourceDirs.isEmpty() ? List.of(Path.of("src/main/gosu")) : sourceDirs);
        List<Path> tests = resolve(projectDir, testDirs.isEmpty() ? List.of(Path.of("src/test/gosu")) : testDirs);
        for (Path dir : sources) {
            if (!Files.isDirectory(dir)) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Source directory does not exist: " + dir);
            }
        }
        for (Path dir : tests) {
            if (!Files.isDirectory(dir)) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Test directory does not exist: " + dir);
            }
        }

        Set<String> known = GosuOperators.available().stream().map(GosuMutationOperator::id).collect(Collectors.toSet());
        Set<String> enabled = operators.stream().map(String::trim).map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String operator : enabled) {
            if (!known.contains(operator)) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "Unknown operator '" + operator + "'. Available: " + known.stream().sorted().toList());
            }
        }
        List<ReportGenerator> generators = ReportFormats.generators(spec.commandLine(), formats);

        int parallelism = threads != null ? threads : Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        if (parallelism < 1 || timeoutSeconds < 1) {
            throw new CommandLine.ParameterException(spec.commandLine(), "--threads and --timeout must be at least 1");
        }
        Path reports = (reportDir != null ? reportDir : projectDir.resolve("build/reports/gosu-mutation")).toAbsolutePath().normalize();

        MutationConfiguration configuration = new MutationConfiguration(projectDir, sources, tests,
                classpath.stream().map(path -> path.toAbsolutePath().normalize()).toList(),
                Duration.ofSeconds(timeoutSeconds), enabled, classes, reports, parallelism);

        ProjectCompiler compiler = new GosuCompilerAdapter();
        TestRunner testRunner = new GosuTestRunner();
        MutationListener listener = quiet ? MutationListener.NONE : new ConsoleProgressListener(System.err);
        MutationEngine engine = new MutationEngine(new IsolatedBaselineRunner(compiler, testRunner),
                new GosuMutationGenerator(), new MutationExecutor(compiler, testRunner), listener);

        deleteStaleBaselineFailure(reports);
        MutationRunReport report;
        try {
            report = engine.run(configuration);
        } catch (BaselineFailedException e) {
            err.println("ERROR: " + e.getMessage());
            err.println("Mutation testing needs a project that compiles and whose tests all pass.");
            writeBaselineFailure(reports, e, err);
            return ExitCodes.FAILURE;
        } catch (RuntimeException e) {
            err.println("ERROR: mutation run failed: " + e);
            return ExitCodes.FAILURE;
        }

        MutationScore score = report.score();
        out.println();
        out.println("Mutants generated : " + score.total());
        out.println("Killed            : " + score.killed());
        out.println("Survived          : " + score.survived());
        out.println("No coverage       : " + score.noCoverage());
        out.println("Timed out         : " + score.timedOut());
        out.println("Compilation errors: " + score.compilationErrors());
        out.println("Run errors        : " + score.runErrors());
        out.println("Not run           : " + score.notRun());
        out.println("Mutation score    : " + (score.killed() + score.survived() == 0 ? "n/a (no mutant was evaluated)"
                : score.mutationScore() + "%  (killed / (killed + survived))"));
        for (ReportGenerator generator : generators) {
            out.println("Report            : " + generator.generate(report, reports));
        }
        out.flush();

        if (threshold != null && score.mutationScore() < threshold) {
            err.println("Mutation score " + score.mutationScore() + "% is below the threshold of " + threshold + "%");
            return ExitCodes.BELOW_THRESHOLD;
        }
        return ExitCodes.OK;
    }

    /** Machine readable copy of a baseline failure, for tools such as the IntelliJ plugin. */
    static final String BASELINE_FAILURE_FILE = "baseline-failure.txt";

    private static void writeBaselineFailure(Path reports, BaselineFailedException failure, PrintWriter err) {
        try {
            Files.createDirectories(reports);
            Files.writeString(reports.resolve(BASELINE_FAILURE_FILE), failure.getMessage() + System.lineSeparator());
        } catch (java.io.IOException e) {
            err.println("WARNING: could not write " + reports.resolve(BASELINE_FAILURE_FILE) + ": " + e);
        }
    }

    private static void deleteStaleBaselineFailure(Path reports) {
        try {
            Files.deleteIfExists(reports.resolve(BASELINE_FAILURE_FILE));
        } catch (java.io.IOException ignored) {
            // a stale file is harmless: it is overwritten on the next failure
        }
    }

    private static List<Path> resolve(Path projectDir, List<Path> dirs) {
        return dirs.stream().map(dir -> projectDir.resolve(dir).normalize()).toList();
    }
}
