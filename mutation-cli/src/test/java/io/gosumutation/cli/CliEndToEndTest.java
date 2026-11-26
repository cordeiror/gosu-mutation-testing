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
    void mutantsListedAsEquivalentInTheTrialAreSkipped() throws IOException {
        Path source = SAMPLE.resolve("src/main/gosu/sample/EligibilityService.gs");
        Path test = SAMPLE.resolve("src/test/gosu/sample/EligibilityServiceTest.gs");
        Path trial = reportDir.resolve("trial");
        Path json = trial.resolve("build/reports/gosu-mutation/mutation-report.json");
        assertEquals(0, execute("class", "--source", source.toString(), "--test", test.toString(),
                "--trial-dir", trial.toString(), "--quiet", "--operators", "RELATIONAL_BOUNDARY"), err.toString());
        MutationRunReport first = JsonReportGenerator.read(json);
        String id = first.results().get(0).mutation().id();
        Files.writeString(trial.resolve(EquivalentMutants.FILE_NAME),
                "# marked by hand\n" + id + "  # sample.EligibilityService line 5\n");

        assertEquals(0, execute("class", "--source", source.toString(), "--test", test.toString(),
                "--trial-dir", trial.toString(), "--quiet", "--operators", "RELATIONAL_BOUNDARY"), err.toString());

        MutationRunReport second = JsonReportGenerator.read(json);
        assertEquals(first.results().size(), second.results().size());
        assertEquals(MutationStatus.EQUIVALENT, second.results().get(0).status());
        assertEquals(1, second.score().equivalent());
        assertEquals(first.score().killed() - 1, second.score().killed());
        assertTrue(out.toString().contains("Equivalent        : 1"), out.toString());
        assertTrue(Files.readString(json.resolveSibling("mutation-report.html")).contains("EQUIVALENT"));
        assertTrue(Files.exists(trial.resolve(EquivalentMutants.FILE_NAME)), "the trial refresh keeps the list");
    }

    /** Two test classes of EligibilityService plus a helper, in a folder tree, like a real test module. */
    private Path splitTestsFolder() throws IOException {
        Path root = reportDir.resolve("other code base/tests");
        Path pkg = root.resolve("sample");
        Files.createDirectories(pkg.resolve("deeper"));
        Files.writeString(pkg.resolve("EligibilityAgeTest.gs"), """
                package sample

                uses org.junit.jupiter.api.Assertions
                uses org.junit.jupiter.api.Test

                class EligibilityAgeTest {
                  @Test
                  function boundary() {
                    Assertions.assertFalse(Ages.service().isEligible(17, true))
                    Assertions.assertTrue(Ages.service().isEligible(18, true))
                  }
                }
                """);
        Files.writeString(pkg.resolve("deeper/EligibilityLicenseTest.gs"), """
                package sample

                uses org.junit.jupiter.api.Assertions
                uses org.junit.jupiter.api.Test

                class EligibilityLicenseTest {
                  @Test
                  function licenseIsRequired() {
                    Assertions.assertFalse(Ages.service().isEligible(40, false))
                  }
                }
                """);
        Files.writeString(pkg.resolve("Ages.gs"), """
                package sample

                class Ages {
                  static function service() : EligibilityService {
                    return new EligibilityService()
                  }
                }
                """);
        Files.writeString(pkg.resolve("notes.txt"), "not a Gosu file");
        return root;
    }

    @Test
    void classCommandAcceptsAFolderOfTestsAndSearchesItsTree() throws IOException {
        Path tests = splitTestsFolder();
        Path trial = reportDir.resolve("trial");

        int exitCode = execute("class", "--source", SAMPLE.resolve("src/main/gosu/sample/EligibilityService.gs").toString(),
                "--test", tests.toString(), "--trial-dir", trial.toString(), "--quiet");

        assertEquals(0, exitCode, err.toString() + out);
        assertTrue(out.toString().contains("Copied 3 Gosu file(s) found under"), out.toString());
        assertTrue(Files.exists(trial.resolve("src/test/gosu/sample/EligibilityLicenseTest.gs")), "copied by package, not by folder");
        assertFalse(Files.exists(trial.resolve("src/test/gosu/sample/notes.txt")));
        MutationRunReport report = JsonReportGenerator.read(trial.resolve("build/reports/gosu-mutation/mutation-report.json"));
        assertEquals(2, report.baselineTestsRun(), "the test methods of both files ran");
        assertTrue(report.results().stream().allMatch(r -> r.status() == MutationStatus.KILLED), report.results().toString());
    }

    @Test
    void classCommandRefreshesItsOwnTrialButKeepsFoldersItDidNotCreate() throws IOException {
        Path source = SAMPLE.resolve("src/main/gosu/sample/EligibilityService.gs");
        Path test = SAMPLE.resolve("src/test/gosu/sample/EligibilityServiceTest.gs");

        // Created by the command: a stale test from an earlier run is removed on the next run.
        Path own = reportDir.resolve("own");
        assertEquals(0, execute("class", "--source", source.toString(), "--test", splitTestsFolder().toString(),
                "--trial-dir", own.toString(), "--quiet"), err.toString());
        assertTrue(Files.exists(own.resolve(".gosu-mutation-trial")));
        assertEquals(0, execute("class", "--source", source.toString(), "--test", test.toString(),
                "--trial-dir", own.toString(), "--quiet"), err.toString());
        assertFalse(Files.exists(own.resolve("src/test/gosu/sample/EligibilityAgeTest.gs")), "stale test removed");
        assertTrue(Files.exists(own.resolve("src/test/gosu/sample/EligibilityServiceTest.gs")));

        // Not created by the command: nothing in it is deleted, and it is not marked.
        Path foreign = reportDir.resolve("foreign");
        Path kept = foreign.resolve("src/test/gosu/sample/KeptTest.gs");
        Files.createDirectories(kept.getParent());
        Files.writeString(kept, "package sample\nclass KeptTest {}\n");
        execute("class", "--source", source.toString(), "--test", test.toString(), "--trial-dir", foreign.toString(), "--quiet");
        assertTrue(Files.exists(kept), "files in a folder the command did not create are kept");
        assertFalse(Files.exists(foreign.resolve(".gosu-mutation-trial")));
        assertTrue(out.toString().contains("was not created by this command"), out.toString());
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
        for (String id : List.of("RELATIONAL_BOUNDARY", "EQUALITY", "LOGICAL", "BOOLEAN_LITERAL", "NUMERIC_LITERAL", "BOOLEAN_RETURN",
                "NEGATION_REMOVAL", "STRING_RETURN")) {
            assertTrue(out.toString().contains(id), out.toString());
        }
    }

    @Test
    void invalidArgumentsAreUsageErrors() {
        assertEquals(2, execute("run", "--project", reportDir.resolve("missing").toString()));
        assertEquals(2, execute("run", "--project", SAMPLE.toString(), "--operators", "NOPE"));
        assertEquals(2, execute("run", "--project", SAMPLE.toString(), "--format", "pdf"));
        assertEquals(2, execute("run", "--project", SAMPLE.toString(), "--equivalent", reportDir.resolve("none.txt").toString()));
        assertTrue(err.toString().contains("Unknown operator 'NOPE'"), err.toString());
    }
}
