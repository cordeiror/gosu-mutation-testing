package io.gosumutation.report;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.gosumutation.core.MutationRunReport;
import io.gosumutation.core.port.ReportGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes (and reads back) {@code mutation-report.json}, the machine readable form of a run. */
public final class JsonReportGenerator implements ReportGenerator {

    public static final String FILE_NAME = "mutation-report.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public String format() {
        return "json";
    }

    @Override
    public Path generate(MutationRunReport report, Path reportDir) {
        try {
            Files.createDirectories(reportDir);
            Path file = reportDir.resolve(FILE_NAME);
            MAPPER.writeValue(file.toFile(), report);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static MutationRunReport read(Path jsonFile) {
        try {
            return MAPPER.readValue(jsonFile.toFile(), MutationRunReport.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + jsonFile, e);
        }
    }

    static String toCompactJson(MutationRunReport report) {
        try {
            return new ObjectMapper().writeValueAsString(report);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
