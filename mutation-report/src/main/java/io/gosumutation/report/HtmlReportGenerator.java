package io.gosumutation.report;

import io.gosumutation.core.MutationRunReport;
import io.gosumutation.core.port.ReportGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@code mutation-report.html}: a single self contained page (no network access, no external
 * assets) with the run data embedded as JSON and rendered in the browser.
 */
public final class HtmlReportGenerator implements ReportGenerator {

    public static final String FILE_NAME = "mutation-report.html";

    private static final String TEMPLATE = "/io/gosumutation/report/report-template.html";
    private static final String DATA_PLACEHOLDER = "/*__REPORT_DATA__*/";

    @Override
    public String format() {
        return "html";
    }

    @Override
    public Path generate(MutationRunReport report, Path reportDir) {
        try (InputStream template = HtmlReportGenerator.class.getResourceAsStream(TEMPLATE)) {
            if (template == null) {
                throw new IllegalStateException("Missing resource " + TEMPLATE);
            }
            String html = new String(template.readAllBytes(), StandardCharsets.UTF_8);
            // Escaping '<' keeps source code such as "</script>" from ending the data block.
            String json = JsonReportGenerator.toCompactJson(report).replace("<", "\\u003c");
            Files.createDirectories(reportDir);
            Path file = reportDir.resolve(FILE_NAME);
            Files.writeString(file, html.replace(DATA_PLACEHOLDER, json), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
