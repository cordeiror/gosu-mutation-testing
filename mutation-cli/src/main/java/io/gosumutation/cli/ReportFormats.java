package io.gosumutation.cli;

import io.gosumutation.core.port.ReportGenerator;
import io.gosumutation.report.HtmlReportGenerator;
import io.gosumutation.report.JsonReportGenerator;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

/** Maps {@code --format} values to report generators. */
final class ReportFormats {

    private static final List<ReportGenerator> ALL = List.of(new HtmlReportGenerator(), new JsonReportGenerator());

    private ReportFormats() { }

    static List<ReportGenerator> generators(CommandLine commandLine, List<String> formats) {
        List<ReportGenerator> selected = new ArrayList<>();
        for (String format : formats) {
            ReportGenerator generator = ALL.stream()
                    .filter(candidate -> candidate.format().equalsIgnoreCase(format.trim()))
                    .findFirst()
                    .orElseThrow(() -> new CommandLine.ParameterException(commandLine,
                            "Unknown report format '" + format + "'. Available: html, json"));
            if (!selected.contains(generator)) {
                selected.add(generator);
            }
        }
        return selected;
    }
}
