package io.gosumutation.core;

import io.gosumutation.core.port.BaselineResult;
import io.gosumutation.core.port.CoverageData;
import io.gosumutation.core.port.MutantExecutor;
import io.gosumutation.core.port.MutationListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The engine is tested through its ports with in-memory stand-ins; the real adapters have their own integration tests. */
class MutationEngineTest {

    private static final MutationConfiguration CONFIGURATION = new MutationConfiguration(Path.of("project"),
            List.of(Path.of("project/src")), List.of(), List.of(), Duration.ofSeconds(5), Set.of(), List.of(),
            Path.of("project/report"), 4);

    private static MutationDescriptor mutant(String file, int line, int offset) {
        return new MutationDescriptor(MutationDescriptor.computeId(file, "OP", offset, "a", "b"), "OP", "project/src", file,
                "demo.Foo", "run", line, 1, offset, offset + 1, "a", "b", "replaced a with b", "a", "b");
    }

    private static BaselineResult passingBaseline(CoverageData coverage) {
        return new BaselineResult(true, 3, List.of(), 10, "", coverage);
    }

    private static CoverageData coverageWithoutLine(int uncoveredLine) {
        return new CoverageData() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean isCovered(String relativeSourcePath, int line) {
                return line != uncoveredLine;
            }
        };
    }

    @Test
    void abortsBeforeGeneratingMutantsWhenTheBaselineFails() {
        List<String> calls = new ArrayList<>();
        MutationEngine engine = new MutationEngine(
                configuration -> BaselineResult.failure("tests fail", List.of("demo.FooTest.x()"), 5),
                configuration -> {
                    calls.add("generate");
                    return List.of();
                },
                (mutant, configuration) -> {
                    calls.add("execute");
                    return MutationResult.of(mutant, MutationStatus.KILLED, 1, "");
                },
                MutationListener.NONE);

        BaselineFailedException failure = assertThrows(BaselineFailedException.class, () -> engine.run(CONFIGURATION));

        assertEquals(List.of("demo.FooTest.x()"), failure.baseline().failedTests());
        assertTrue(calls.isEmpty());
    }

    @Test
    void reportsResultsInDeterministicOrderRegardlessOfSchedulingOrInputOrder() {
        List<MutationDescriptor> generated = List.of(mutant("b/B.gs", 2, 30), mutant("a/A.gs", 9, 90), mutant("a/A.gs", 1, 10));
        MutantExecutor slowFirst = (mutant, configuration) -> {
            try {
                Thread.sleep(mutant.startOffset() == 10 ? 150 : 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return MutationResult.of(mutant, MutationStatus.SURVIVED, 1, "");
        };

        MutationRunReport report = new MutationEngine(c -> passingBaseline(CoverageData.UNAVAILABLE), c -> generated,
                slowFirst, MutationListener.NONE).run(CONFIGURATION);

        assertEquals(List.of(10, 90, 30), report.results().stream().map(r -> r.mutation().startOffset()).toList());
        assertEquals(3, report.baselineTestsRun());
        assertFalse(report.coverageAvailable());
    }

    @Test
    void uncoveredMutantsAreNotExecuted() {
        List<MutationDescriptor> executed = Collections.synchronizedList(new ArrayList<>());
        MutationRunReport report = new MutationEngine(c -> passingBaseline(coverageWithoutLine(7)),
                c -> List.of(mutant("a/A.gs", 7, 70), mutant("a/A.gs", 8, 80)),
                (mutant, configuration) -> {
                    executed.add(mutant);
                    return new MutationResult(mutant, MutationStatus.KILLED, List.of("demo.FooTest.x()"), 1, "");
                },
                MutationListener.NONE).run(CONFIGURATION);

        assertEquals(MutationStatus.NO_COVERAGE, report.results().get(0).status());
        assertEquals(MutationStatus.KILLED, report.results().get(1).status());
        assertEquals(List.of(80), executed.stream().map(MutationDescriptor::startOffset).toList());
        assertEquals(100.0, report.score().mutationScore());
        assertEquals(50.0, report.score().scoreIncludingUncovered());
    }

    @Test
    void mutantsMarkedAsEquivalentAreNotExecutedNorScored() {
        MutationDescriptor equivalent = mutant("a/A.gs", 1, 10);
        MutationConfiguration configuration = new MutationConfiguration(Path.of("project"), List.of(Path.of("project/src")),
                List.of(), List.of(), Duration.ofSeconds(5), Set.of(), List.of(), Path.of("project/report"), 2,
                Set.of(equivalent.id()));
        List<MutationDescriptor> executed = Collections.synchronizedList(new ArrayList<>());

        MutationRunReport report = new MutationEngine(c -> passingBaseline(CoverageData.UNAVAILABLE),
                c -> List.of(equivalent, mutant("a/A.gs", 2, 20)),
                (mutant, c) -> {
                    executed.add(mutant);
                    return MutationResult.of(mutant, MutationStatus.SURVIVED, 1, "");
                },
                MutationListener.NONE).run(configuration);

        assertEquals(MutationStatus.EQUIVALENT, report.results().get(0).status());
        assertEquals(List.of(20), executed.stream().map(MutationDescriptor::startOffset).toList());
        assertEquals(1, report.score().equivalent());
        assertEquals(2, report.score().total());
        assertEquals(0.0, report.score().mutationScore());
        assertEquals(1, report.score().survived());
    }

    @Test
    void anExecutorFailureBecomesRunErrorAndTheRunContinues() {
        MutationRunReport report = new MutationEngine(c -> passingBaseline(CoverageData.UNAVAILABLE),
                c -> List.of(mutant("a/A.gs", 1, 10), mutant("a/A.gs", 2, 20)),
                (mutant, configuration) -> {
                    if (mutant.startOffset() == 10) {
                        throw new IllegalStateException("disk full");
                    }
                    return MutationResult.of(mutant, MutationStatus.SURVIVED, 1, "");
                },
                MutationListener.NONE).run(CONFIGURATION);

        assertEquals(MutationStatus.RUN_ERROR, report.results().get(0).status());
        assertTrue(report.results().get(0).details().contains("disk full"));
        assertEquals(MutationStatus.SURVIVED, report.results().get(1).status());
    }

    @Test
    void listenerSeesEveryStage() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        MutationListener listener = new MutationListener() {
            @Override
            public void baselineStarted() {
                events.add("baselineStarted");
            }

            @Override
            public void baselineFinished(BaselineResult baseline) {
                events.add("baselineFinished");
            }

            @Override
            public void mutantsGenerated(List<MutationDescriptor> mutants) {
                events.add("generated:" + mutants.size());
            }

            @Override
            public void mutantFinished(MutationResult result) {
                events.add("finished:" + result.status());
            }

            @Override
            public void runFinished(MutationRunReport report) {
                events.add("runFinished");
            }
        };

        new MutationEngine(c -> passingBaseline(CoverageData.UNAVAILABLE), c -> List.of(mutant("a/A.gs", 1, 10)),
                (mutant, configuration) -> MutationResult.of(mutant, MutationStatus.TIMED_OUT, 1, ""), listener).run(CONFIGURATION);

        assertEquals(List.of("baselineStarted", "baselineFinished", "generated:1", "finished:TIMED_OUT", "runFinished"), events);
    }
}
