package io.gosumutation.report;

import io.gosumutation.core.MutationDescriptor;
import io.gosumutation.core.MutationResult;
import io.gosumutation.core.MutationRunReport;
import io.gosumutation.core.MutationScore;
import io.gosumutation.core.MutationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGeneratorsTest {

    @TempDir
    Path reportDir;

    private static MutationRunReport sampleReport() {
        MutationDescriptor killed = new MutationDescriptor("aaaaaaaaaaaa", "LOGICAL", "/p/src", "demo/Foo.gs", "demo.Foo",
                "check", 5, 20, 80, 83, "and", "or", "replaced and with or", "return a and b", "return a or b");
        MutationDescriptor survived = new MutationDescriptor("bbbbbbbbbbbb", "BOOLEAN_RETURN", "/p/src", "demo/Foo.gs", "demo.Foo",
                "html", 9, 12, 120, 140, "s == \"</script>\"", "true", "replaced boolean return value with true",
                "return s == \"</script>\"", "return true");
        List<MutationResult> results = List.of(
                new MutationResult(killed, MutationStatus.KILLED, List.of("demo.FooTest.rejects()"), 3210, ""),
                MutationResult.of(survived, MutationStatus.SURVIVED, 2990, ""));
        return new MutationRunReport(MutationRunReport.SCHEMA_VERSION, "2026-01-01T00:00:00Z", "/p", List.of("/p/src"),
                List.of("/p/test"), 60, 2, List.of("BOOLEAN_RETURN", "LOGICAL"), 4, 1500, true, 9000,
                MutationScore.of(results), results);
    }

    @Test
    void jsonRoundTripsTheWholeReport() {
        MutationRunReport report = sampleReport();

        Path file = new JsonReportGenerator().generate(report, reportDir);

        assertEquals("mutation-report.json", file.getFileName().toString());
        assertEquals(report, JsonReportGenerator.read(file));
    }

    @Test
    void jsonExposesScoreAndKillingTest() throws IOException {
        String json = Files.readString(new JsonReportGenerator().generate(sampleReport(), reportDir));

        assertTrue(json.contains("\"mutationScore\" : 50.0"), json);
        assertTrue(json.contains("\"status\" : \"KILLED\""), json);
        assertTrue(json.contains("demo.FooTest.rejects()"), json);
    }

    @Test
    void htmlIsSelfContainedAndEmbedsTheData() throws IOException {
        Path file = new HtmlReportGenerator().generate(sampleReport(), reportDir);
        String html = Files.readString(file);

        assertEquals("mutation-report.html", file.getFileName().toString());
        assertFalse(html.contains("/*__REPORT_DATA__*/"), "placeholder must be replaced");
        assertTrue(html.contains("\"id\":\"aaaaaaaaaaaa\""));
        assertTrue(html.contains("demo.FooTest.rejects()"));
        assertFalse(html.contains("http://") || html.contains("https://"), "no external resources");
        // Source text containing </script> must not terminate the embedded data block.
        assertEquals(2, html.split("</script>", -1).length - 1);
    }
}
