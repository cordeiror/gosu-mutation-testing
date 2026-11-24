package io.gosumutation.cli;

import io.gosumutation.core.MutationRunReport;
import io.gosumutation.core.port.ReportGenerator;
import io.gosumutation.report.JsonReportGenerator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "report", mixinStandardHelpOptions = true,
        description = "Regenerates reports from the mutation-report.json of a previous run, without running any test.")
final class ReportCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"-p", "--project"}, paramLabel = "<dir>", defaultValue = ".",
            description = "Project root used to locate the default input (default: current directory).")
    Path project;

    @Option(names = {"-i", "--input"}, paramLabel = "<file>",
            description = "JSON report to read (default: <project>/build/reports/gosu-mutation/mutation-report.json).")
    Path input;

    @Option(names = {"-r", "--report-dir"}, paramLabel = "<dir>",
            description = "Where reports are written (default: the directory of the input file).")
    Path reportDir;

    @Option(names = {"-f", "--format"}, paramLabel = "<format>", split = ",", defaultValue = "html",
            description = "Report formats: html, json (default: ${DEFAULT-VALUE}).")
    List<String> formats;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        List<ReportGenerator> generators = ReportFormats.generators(spec.commandLine(), formats);
        Path json = (input != null ? input
                : project.resolve("build/reports/gosu-mutation").resolve(JsonReportGenerator.FILE_NAME)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(json)) {
            spec.commandLine().getErr().println("ERROR: no JSON report at " + json + ". Run 'gosu-mutation run' first or pass --input.");
            return ExitCodes.FAILURE;
        }
        MutationRunReport report;
        try {
            report = JsonReportGenerator.read(json);
        } catch (UncheckedIOException e) {
            spec.commandLine().getErr().println("ERROR: " + e.getMessage() + ": " + e.getCause().getMessage());
            return ExitCodes.FAILURE;
        }
        Path target = (reportDir != null ? reportDir : json.getParent()).toAbsolutePath().normalize();
        for (ReportGenerator generator : generators) {
            out.println("Report: " + generator.generate(report, target));
        }
        out.flush();
        return ExitCodes.OK;
    }
}
