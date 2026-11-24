package io.gosumutation.runner;

import io.gosumutation.core.BaselineFailedException;
import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.MutationEngine;
import io.gosumutation.core.MutationResult;
import io.gosumutation.core.MutationRunReport;
import io.gosumutation.core.MutationStatus;
import io.gosumutation.core.port.MutationListener;
import io.gosumutation.gosu.GosuCompilerAdapter;
import io.gosumutation.gosu.GosuMutationGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end: real Gosu parser, real Gosu compiler and real JUnit runs in child JVMs. No mocks.
 */
class MutationPipelineIntegrationTest {

    @TempDir
    Path projectDir;
    Path mainDir;
    Path testDir;

    @BeforeEach
    void createProject() throws IOException {
        mainDir = projectDir.resolve("src/main/gosu");
        testDir = projectDir.resolve("src/test/gosu");
        write(mainDir, "demo/EligibilityService.gs", """
                package demo

                class EligibilityService {
                  function isEligible(age : int, hasLicense : boolean) : boolean {
                    return age >= 18 and hasLicense
                  }

                  function neverCalled() : boolean {
                    return false
                  }
                }
                """);
        // No test for age == 18: the ">=" to ">" mutant must survive.
        write(testDir, "demo/EligibilityServiceTest.gs", """
                package demo

                uses org.junit.jupiter.api.Assertions
                uses org.junit.jupiter.api.Test

                class EligibilityServiceTest {
                  @Test
                  function adultWithLicenseIsEligible() {
                    Assertions.assertTrue(new EligibilityService().isEligible(30, true))
                  }

                  @Test
                  function minorIsNotEligible() {
                    Assertions.assertFalse(new EligibilityService().isEligible(17, true))
                  }
                }
                """);
    }

    private static void write(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private MutationRunReport run(Set<String> operators, List<String> classes, Duration timeout) {
        MutationConfiguration configuration = new MutationConfiguration(projectDir, List.of(mainDir), List.of(testDir),
                List.of(), timeout, operators, classes, projectDir.resolve("report"), 3);
        GosuCompilerAdapter compiler = new GosuCompilerAdapter();
        GosuTestRunner testRunner = new GosuTestRunner();
        return new MutationEngine(new IsolatedBaselineRunner(compiler, testRunner), new GosuMutationGenerator(),
                new MutationExecutor(compiler, testRunner), MutationListener.NONE).run(configuration);
    }

    private static MutationResult find(MutationRunReport report, String original, String replacement) {
        return report.results().stream()
                .filter(r -> r.mutation().originalText().equals(original) && r.mutation().replacementText().equals(replacement))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no mutant " + original + " -> " + replacement + " in " + report.results()));
    }

    private Map<String, String> snapshot() throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(projectDir.resolve("src"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(projectDir.relativize(path).toString(), Files.readString(path));
            }
        }
        return files;
    }

    @Test
    void classifiesKilledSurvivedAndUncoveredMutantsWithoutTouchingTheProject() throws IOException {
        Map<String, String> before = snapshot();

        MutationRunReport report = run(Set.of(), List.of(), Duration.ofSeconds(120));

        assertEquals(2, report.baselineTestsRun());
        assertTrue(report.coverageAvailable());

        MutationResult boundary = find(report, ">=", ">");
        assertEquals(MutationStatus.SURVIVED, boundary.status(), boundary.details());

        MutationResult logical = find(report, "and", "or");
        assertEquals(MutationStatus.KILLED, logical.status(), logical.details());
        assertEquals(List.of("demo.EligibilityServiceTest.minorIsNotEligible()"), logical.killingTests());

        assertEquals(MutationStatus.KILLED, find(report, "age >= 18 and hasLicense", "false").status());
        assertEquals(MutationStatus.KILLED, find(report, "age >= 18 and hasLicense", "true").status());
        assertEquals(MutationStatus.NO_COVERAGE, find(report, "false", "true").status());

        // 18 -> 19: ages 17 and 30 cannot tell the difference.
        assertEquals(MutationStatus.SURVIVED, find(report, "18", "19").status());

        assertEquals(6, report.score().total());
        assertEquals(3, report.score().killed());
        assertEquals(2, report.score().survived());
        assertEquals(1, report.score().noCoverage());
        assertEquals(60.0, report.score().mutationScore());
        assertEquals(50.0, report.score().scoreIncludingUncovered());

        assertEquals(before, snapshot(), "the project sources must be untouched");
        try (Stream<Path> projectFiles = Files.list(projectDir)) {
            assertEquals(List.of("src"), projectFiles.map(p -> p.getFileName().toString()).toList(),
                    "nothing may be created inside the project");
        }
    }

    @Test
    void refusesToMutateWhenTheOriginalTestsFail() throws IOException {
        write(testDir, "demo/BrokenTest.gs", """
                package demo

                uses org.junit.jupiter.api.Assertions
                uses org.junit.jupiter.api.Test

                class BrokenTest {
                  @Test
                  function alwaysFails() {
                    Assertions.assertTrue(new EligibilityService().isEligible(1, true))
                  }
                }
                """);

        BaselineFailedException failure = assertThrows(BaselineFailedException.class,
                () -> run(Set.of(), List.of(), Duration.ofSeconds(120)));

        assertEquals(List.of("demo.BrokenTest.alwaysFails()"), failure.baseline().failedTests());
    }

    @Test
    void timeoutsAndInvalidMutantsAreNotCountedAsKilled() throws IOException {
        write(mainDir, "demo/Looper.gs", """
                package demo

                class Looper {
                  var _max : int = 2147483647

                  function countToThree() : int {
                    var i = 0
                    while (i != 3) {
                      i = i + 1
                    }
                    return i
                  }
                }
                """);
        write(testDir, "demo/LooperTest.gs", """
                package demo

                uses org.junit.jupiter.api.Assertions
                uses org.junit.jupiter.api.Test

                class LooperTest {
                  @Test
                  function countsToThree() {
                    Assertions.assertEquals(3, new Looper().countToThree())
                  }
                }
                """);

        MutationRunReport report = run(Set.of("NUMERIC_LITERAL"), List.of("Looper"), Duration.ofSeconds(25));

        // "i + 1" -> "i + 2" skips over 3 and loops forever.
        assertEquals(MutationStatus.TIMED_OUT, find(report, "1", "2").status());
        // 2147483648 is not an int.
        MutationResult overflow = find(report, "2147483647", "2147483648");
        assertEquals(MutationStatus.COMPILATION_ERROR, overflow.status(), overflow.details());
        assertEquals(MutationStatus.KILLED, find(report, "3", "4").status());

        assertEquals(1, report.score().timedOut());
        assertEquals(1, report.score().compilationErrors());
        assertEquals(report.score().killed() + report.score().survived() + 2, report.score().total());
    }
}
