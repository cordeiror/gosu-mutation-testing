package io.gosumutation.cli;

import io.gosumutation.core.MutationResult;
import io.gosumutation.core.MutationRunReport;
import io.gosumutation.core.MutationStatus;
import io.gosumutation.report.JsonReportGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the real command line against sample-gosu-project: the MVP acceptance criteria as a test. */
class CliEndToEndTest {

    private static final Path SAMPLE = Path.of(System.getProperty("sample.project.dir"));

    @TempDir
    Path reportDir;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int execute(String... args) {
        return new CommandLine(new Main())
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(args);
    }

    private static Map<String, String> snapshot(Path dir) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(dir.relativize(path).toString(), Files.readString(path));
            }
        }
        return files;
    }

    @Test
    void runsTheSampleProjectAndFindsKilledAndSurvivingMutants() throws IOException {
        Map<String, String> before = snapshot(SAMPLE.resolve("src"));

        int exitCode = execute("run", "--project", SAMPLE.toString(), "--report-dir", reportDir.toString(), "--quiet");

        assertEquals(0, exitCode, err.toString());
        assertEquals(before, snapshot(SAMPLE.resolve("src")), "original sources must not change");

        Path json = reportDir.resolve("mutation-report.json");
        Path html = reportDir.resolve("mutation-report.html");
        assertTrue(Files.size(json) > 0);
        assertTrue(Files.readString(html).contains("sample.EligibilityService"));

        MutationRunReport report = JsonReportGenerator.read(json);
        List<MutationResult> eligibility = report.results().stream()
                .filter(r -> r.mutation().className().equals("sample.EligibilityService")).toList();
        List<MutationResult> discount = report.results().stream()
                .filter(r -> r.mutation().className().equals("sample.DiscountCalculator")).toList();

        // The fully tested class: every mutant is killed, and we know by which test.
        assertEquals(5, eligibility.size());
        assertTrue(eligibility.stream().allMatch(r -> r.status() == MutationStatus.KILLED), eligibility.toString());
        assertTrue(eligibility.stream().noneMatch(r -> r.killingTests().isEmpty()));
        MutationResult boundary = eligibility.stream().filter(r -> r.mutation().originalText().equals(">=")).findFirst().orElseThrow();
        assertEquals(List.of("sample.EligibilityServiceTest.exactlyEighteenIsEligible()"), boundary.killingTests());

        // The class with incomplete tests: covered lines whose mutants survive, plus untested code.
        assertTrue(discount.stream().anyMatch(r -> r.status() == MutationStatus.SURVIVED));
        assertTrue(discount.stream().anyMatch(r -> r.status() == MutationStatus.KILLED));
        assertTrue(discount.stream().filter(r -> r.mutation().methodName().equals("isBulkOrder"))
                .allMatch(r -> r.status() == MutationStatus.NO_COVERAGE));
        assertTrue(discount.stream().filter(r -> r.mutation().methodName().equals("discountFor"))
                .noneMatch(r -> r.status() == MutationStatus.NO_COVERAGE), "discountFor has full line coverage");

        assertTrue(out.toString().contains("Mutation score    : " + report.score().mutationScore() + "%"), out.toString());
    }

    @Test
    void classAndOperatorSelectionNarrowTheRunAndThresholdSetsTheExitCode() {
        int exitCode = execute("run", "--project", SAMPLE.toString(), "--report-dir", reportDir.toString(), "--quiet",
                "--class", "DiscountCalculator", "--operators", "relational_boundary", "--format", "json",
                "--threads", "2", "--timeout", "120", "--threshold", "90");

        assertEquals(3, exitCode, err.toString());
        assertFalse(Files.exists(reportDir.resolve("mutation-report.html")));
        MutationRunReport report = JsonReportGenerator.read(reportDir.resolve("mutation-report.json"));
        assertEquals(List.of("RELATIONAL_BOUNDARY"), report.operators());
        assertTrue(report.results().stream().allMatch(r -> r.mutation().className().equals("sample.DiscountCalculator")));
        assertEquals(3, report.results().size());
        assertEquals(2, report.parallelism());
        assertEquals(120, report.timeoutSeconds());
    }

    @Test
    void classCommandCopiesClassAndTestIntoATrialProjectAndMutatesOnlyThatClass() throws IOException {
        Path source = SAMPLE.resolve("src/main/gosu/sample/EligibilityService.gs");
        Path test = SAMPLE.resolve("src/test/gosu/sample/EligibilityServiceTest.gs");
        Path trial = reportDir.resolve("trial project");
        Map<String, String> before = snapshot(SAMPLE.resolve("src"));

        int exitCode = execute("class", "--source", source.toString(), "--test", test.toString(),
                "--trial-dir", trial.toString(), "--quiet", "--threads", "2");

        assertEquals(0, exitCode, err.toString());
        assertEquals(before, snapshot(SAMPLE.resolve("src")), "originals must not change");
        assertEquals(Files.readString(source), Files.readString(trial.resolve("src/main/gosu/sample/EligibilityService.gs")));
        assertTrue(Files.exists(trial.resolve("src/test/gosu/sample/EligibilityServiceTest.gs")));
        MutationRunReport report = JsonReportGenerator.read(trial.resolve("build/reports/gosu-mutation/mutation-report.json"));
        assertEquals(5, report.results().size());
        assertTrue(report.results().stream().allMatch(r -> r.status() == MutationStatus.KILLED));
        assertEquals(2, report.parallelism(), "unknown options are passed through to run");
    }

    @Test
    void classCommandExplainsMissingTestsAndNestedFolders() throws IOException {
        Path nested = reportDir.resolve("trial/extracted/src/test/gosu/sample");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("XTest.gs"), "package sample\nclass XTest {}\n");

        int exitCode = execute("class", "--source", SAMPLE.resolve("src/main/gosu/sample/EligibilityService.gs").toString(),
                "--trial-dir", reportDir.resolve("trial").toString());

        assertEquals(1, exitCode);
        assertTrue(err.toString().contains("no Gosu tests found"), err.toString());
        assertTrue(err.toString().contains("extracted"), err.toString());
        assertFalse(Files.exists(reportDir.resolve("trial/src/main/gosu")), "nothing is copied when inputs are invalid");
    }

    @Test
    void baselineFailureIsWrittenToAFileForTools() throws IOException {
        Path trial = reportDir.resolve("broken");
        Files.createDirectories(trial.resolve("src/main/gosu/demo"));
        Files.createDirectories(trial.resolve("src/test/gosu/demo"));
        Files.writeString(trial.resolve("src/main/gosu/demo/Broken.gs"),
                "package demo\nclass Broken {\n  function f() : MissingType { return null }\n}\n");
        Files.writeString(trial.resolve("src/test/gosu/demo/BrokenTest.gs"), "package demo\nclass BrokenTest {}\n");

        int exitCode = execute("run", "--project", trial.toString(), "--quiet");

        assertEquals(1, exitCode);
        String failure = Files.readString(trial.resolve("build/reports/gosu-mutation/baseline-failure.txt"));
        assertTrue(failure.contains("does not compile"), failure);
        assertTrue(failure.contains("MissingType"), failure);
    }

    @Test
    void reportCommandRebuildsHtmlFromJson() throws IOException {
        Files.copy(Path.of("src/test/resources/mutation-report.json"), reportDir.resolve("mutation-report.json"));

        int exitCode = execute("report", "--input", reportDir.resolve("mutation-report.json").toString(), "--format", "html");

        assertEquals(0, exitCode, err.toString());
        assertTrue(Files.readString(reportDir.resolve("mutation-report.html")).contains("0123456789ab"));
    }

    @Test
    void reportCommandFailsClearlyWithoutInput() {
        assertEquals(1, execute("report", "--project", reportDir.toString()));
        assertTrue(err.toString().contains("no JSON report"), err.toString());
    }

    @Test
    void operatorsCommandListsEveryBuiltInOperator() {
        assertEquals(0, execute("operators"));
        for (String id : List.of("RELATIONAL_BOUNDARY", "EQUALITY", "LOGICAL", "BOOLEAN_LITERAL", "NUMERIC_LITERAL", "BOOLEAN_RETURN")) {
            assertTrue(out.toString().contains(id), out.toString());
        }
    }

    @Test
    void invalidArgumentsAreUsageErrors() {
        assertEquals(2, execute("run", "--project", reportDir.resolve("missing").toString()));
        assertEquals(2, execute("run", "--project", SAMPLE.toString(), "--operators", "NOPE"));
        assertEquals(2, execute("run", "--project", SAMPLE.toString(), "--format", "pdf"));
        assertTrue(err.toString().contains("Unknown operator 'NOPE'"), err.toString());
    }
}
