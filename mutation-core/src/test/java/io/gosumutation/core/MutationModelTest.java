package io.gosumutation.core;

import io.gosumutation.core.process.ForkedJvm;
import io.gosumutation.core.process.ProcessIsolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationModelTest {

    private static MutationDescriptor descriptor(int start, String original, String replacement) {
        return new MutationDescriptor(MutationDescriptor.computeId("demo/Foo.gs", "OP", start, original, replacement), "OP",
                "/src", "demo/Foo.gs", "demo.Foo", "run", 1, 1, start, start + original.length(), original, replacement,
                "d", "o", "m");
    }

    private static MutationResult result(MutationStatus status) {
        return MutationResult.of(descriptor(0, "a", "b"), status, 1, "");
    }

    @Test
    void scoreCountsOnlyKilledAndSurvivedMutants() {
        List<MutationResult> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(result(MutationStatus.KILLED));
        }
        results.add(result(MutationStatus.SURVIVED));
        results.add(result(MutationStatus.NO_COVERAGE));
        results.add(result(MutationStatus.NO_COVERAGE));
        results.add(result(MutationStatus.TIMED_OUT));
        results.add(result(MutationStatus.COMPILATION_ERROR));
        results.add(result(MutationStatus.RUN_ERROR));
        results.add(result(MutationStatus.NOT_RUN));

        MutationScore score = MutationScore.of(results);

        assertEquals(10, score.total());
        assertEquals(3, score.killed());
        assertEquals(1, score.survived());
        assertEquals(2, score.noCoverage());
        assertEquals(1, score.timedOut());
        assertEquals(1, score.compilationErrors());
        assertEquals(1, score.runErrors());
        assertEquals(1, score.notRun());
        assertEquals(75.0, score.mutationScore());
        assertEquals(50.0, score.scoreIncludingUncovered());
    }

    @Test
    void scoreIsZeroWhenNothingWasEvaluated() {
        MutationScore score = MutationScore.of(List.of(result(MutationStatus.TIMED_OUT), result(MutationStatus.COMPILATION_ERROR)));

        assertEquals(0.0, score.mutationScore());
        assertEquals(0.0, score.scoreIncludingUncovered());
    }

    @Test
    void scoreIsRoundedToOneDecimal() {
        MutationScore score = MutationScore.of(List.of(result(MutationStatus.KILLED), result(MutationStatus.KILLED),
                result(MutationStatus.SURVIVED)));

        assertEquals(66.7, score.mutationScore());
    }

    @Test
    void idsAreDeterministicAndDependOnEveryPartOfTheMutation() {
        String id = MutationDescriptor.computeId("demo/Foo.gs", "OP", 10, ">=", ">");

        assertEquals(12, id.length());
        assertEquals(id, MutationDescriptor.computeId("demo/Foo.gs", "OP", 10, ">=", ">"));
        assertNotEquals(id, MutationDescriptor.computeId("demo/Bar.gs", "OP", 10, ">=", ">"));
        assertNotEquals(id, MutationDescriptor.computeId("demo/Foo.gs", "OTHER", 10, ">=", ">"));
        assertNotEquals(id, MutationDescriptor.computeId("demo/Foo.gs", "OP", 11, ">=", ">"));
        assertNotEquals(id, MutationDescriptor.computeId("demo/Foo.gs", "OP", 10, ">=", "<"));
    }

    @Test
    void applyReplacesOnlyTheRecordedRange() {
        assertEquals("return a > b >= c", descriptor(9, ">=", ">").applyTo("return a >= b >= c"));
    }

    @Test
    void applyRefusesSourceThatChangedSinceAnalysis() {
        assertThrows(IllegalStateException.class, () -> descriptor(9, ">=", ">").applyTo("return a <= b"));
        assertThrows(IllegalStateException.class, () -> descriptor(9, ">=", ">").applyTo("short"));
    }

    @Test
    void classFiltersMatchSimpleAndQualifiedNamesWithWildcards() {
        assertTrue(configuration(List.of(), Set.of()).selectsClass("demo.Foo"));
        assertTrue(configuration(List.of("Foo"), Set.of()).selectsClass("demo.Foo"));
        assertTrue(configuration(List.of("demo.Foo"), Set.of()).selectsClass("demo.Foo"));
        assertTrue(configuration(List.of("demo.*"), Set.of()).selectsClass("demo.Foo"));
        assertTrue(configuration(List.of("*Service"), Set.of()).selectsClass("demo.EligibilityService"));
        assertFalse(configuration(List.of("Fo"), Set.of()).selectsClass("demo.Foo"));
        assertFalse(configuration(List.of("other.Foo"), Set.of()).selectsClass("demo.Foo"));
        assertFalse(configuration(List.of("demoXFoo"), Set.of()).selectsClass("demo.Foo"), "'.' is literal");
    }

    @Test
    void operatorSelectionDefaultsToAll() {
        assertTrue(configuration(List.of(), Set.of()).enablesOperator("LOGICAL"));
        assertTrue(configuration(List.of(), Set.of("LOGICAL")).enablesOperator("LOGICAL"));
        assertFalse(configuration(List.of(), Set.of("LOGICAL")).enablesOperator("EQUALITY"));
    }

    @Test
    void configurationRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new MutationConfiguration(Path.of("p"), List.of(), List.of(),
                List.of(), Duration.ofSeconds(1), Set.of(), List.of(), Path.of("r"), 1));
        assertThrows(IllegalArgumentException.class, () -> new MutationConfiguration(Path.of("p"), List.of(Path.of("s")),
                List.of(), List.of(), Duration.ZERO, Set.of(), List.of(), Path.of("r"), 1));
        assertThrows(IllegalArgumentException.class, () -> new MutationConfiguration(Path.of("p"), List.of(Path.of("s")),
                List.of(), List.of(), Duration.ofSeconds(1), Set.of(), List.of(), Path.of("r"), 0));
    }

    private static MutationConfiguration configuration(List<String> classes, Set<String> operators) {
        return new MutationConfiguration(Path.of("p"), List.of(Path.of("s")), List.of(), List.of(), Duration.ofSeconds(1),
                operators, classes, Path.of("r"), 1);
    }

    @Test
    void processIsolationKillsAProcessThatExceedsItsTimeout(@TempDir Path workDir) throws IOException {
        // A child JVM without a main class argument would exit at once, so it runs a class that blocks.
        List<String> command = ForkedJvm.command(workDir, List.of(), ForkedJvm.frameworkClasspath(),
                Sleeper.class.getName(), List.of());

        long start = System.nanoTime();
        ProcessIsolation.ProcessResult result = ProcessIsolation.run(command, workDir, Duration.ofSeconds(2));
        long seconds = (System.nanoTime() - start) / 1_000_000_000;

        assertTrue(result.timedOut());
        assertTrue(seconds < 20, "the child must be killed, not waited for");
    }

    @Test
    void processIsolationCapturesOutputAndExitCode(@TempDir Path workDir) throws IOException {
        List<String> command = ForkedJvm.command(workDir, List.of(), ForkedJvm.frameworkClasspath(),
                Sleeper.class.getName(), List.of("exit"));

        ProcessIsolation.ProcessResult result = ProcessIsolation.run(command, workDir, Duration.ofSeconds(60));

        assertFalse(result.timedOut());
        assertEquals(7, result.exitCode());
        assertEquals("bye", result.output());
    }

    /** Child process used by the process tests. */
    public static final class Sleeper {
        public static void main(String[] args) throws InterruptedException {
            if (args.length > 0) {
                System.out.println("bye");
                System.exit(7);
            }
            Thread.sleep(120_000);
        }
    }
}
