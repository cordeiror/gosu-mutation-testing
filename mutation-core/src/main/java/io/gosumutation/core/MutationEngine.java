package io.gosumutation.core;

import io.gosumutation.core.port.BaselineResult;
import io.gosumutation.core.port.BaselineRunner;
import io.gosumutation.core.port.CoverageData;
import io.gosumutation.core.port.MutantExecutor;
import io.gosumutation.core.port.MutationGenerator;
import io.gosumutation.core.port.MutationListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates a mutation run. It knows nothing about Gosu, compilers or test frameworks: all of
 * that is reached through the ports in {@code io.gosumutation.core.port}.
 *
 * <ol>
 *   <li>run the baseline (compile + tests on the unmodified project) and require it to pass;</li>
 *   <li>generate the mutants;</li>
 *   <li>evaluate each mutant in isolation, {@code parallelism} at a time (mutants marked as
 *       equivalent are skipped);</li>
 *   <li>aggregate the results.</li>
 * </ol>
 */
public final class MutationEngine {

    private static final Comparator<MutationDescriptor> DETERMINISTIC_ORDER = Comparator
            .comparing(MutationDescriptor::relativePath)
            .thenComparingInt(MutationDescriptor::startOffset)
            .thenComparing(MutationDescriptor::operatorId)
            .thenComparing(MutationDescriptor::replacementText);

    private final BaselineRunner baselineRunner;
    private final MutationGenerator generator;
    private final MutantExecutor executor;
    private final MutationListener listener;

    public MutationEngine(BaselineRunner baselineRunner, MutationGenerator generator, MutantExecutor executor,
                          MutationListener listener) {
        this.baselineRunner = baselineRunner;
        this.generator = generator;
        this.executor = executor;
        this.listener = listener == null ? MutationListener.NONE : listener;
    }

    /**
     * @throws BaselineFailedException when the unmodified project does not compile or has failing tests
     */
    public MutationRunReport run(MutationConfiguration configuration) {
        long start = System.nanoTime();

        listener.baselineStarted();
        BaselineResult baseline = baselineRunner.run(configuration);
        listener.baselineFinished(baseline);
        if (!baseline.passed()) {
            throw new BaselineFailedException(baseline);
        }

        List<MutationDescriptor> mutants = new ArrayList<>(generator.generate(configuration));
        mutants.sort(DETERMINISTIC_ORDER);
        listener.mutantsGenerated(mutants);

        List<MutationResult> results = evaluateAll(mutants, configuration, baseline.coverage());
        MutationScore score = MutationScore.of(results);
        long totalMillis = (System.nanoTime() - start) / 1_000_000;

        MutationRunReport report = new MutationRunReport(
                MutationRunReport.SCHEMA_VERSION,
                Instant.now().toString(),
                configuration.projectDir().toString(),
                configuration.sourceDirs().stream().map(Object::toString).toList(),
                configuration.testDirs().stream().map(Object::toString).toList(),
                configuration.mutantTimeout().toSeconds(),
                configuration.parallelism(),
                mutants.stream().map(MutationDescriptor::operatorId).distinct().sorted().toList(),
                baseline.testsRun(),
                baseline.durationMillis(),
                baseline.coverage().available(),
                totalMillis,
                score,
                results);
        listener.runFinished(report);
        return report;
    }

    private List<MutationResult> evaluateAll(List<MutationDescriptor> mutants, MutationConfiguration configuration,
                                             CoverageData coverage) {
        ExecutorService pool = Executors.newFixedThreadPool(configuration.parallelism(), runnable -> {
            Thread thread = new Thread(runnable, "mutant-worker");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<MutationResult>> futures = new ArrayList<>(mutants.size());
            for (MutationDescriptor mutant : mutants) {
                futures.add(pool.submit(() -> evaluate(mutant, configuration, coverage)));
            }
            // Results are collected in submission order, so the report does not depend on scheduling.
            List<MutationResult> results = new ArrayList<>(mutants.size());
            boolean interrupted = false;
            for (int i = 0; i < futures.size(); i++) {
                MutationDescriptor mutant = mutants.get(i);
                if (interrupted) {
                    results.add(MutationResult.of(mutant, MutationStatus.NOT_RUN, 0, "Run was interrupted"));
                    continue;
                }
                try {
                    results.add(futures.get(i).get());
                } catch (InterruptedException e) {
                    interrupted = true;
                    pool.shutdownNow();
                    results.add(MutationResult.of(mutant, MutationStatus.NOT_RUN, 0, "Run was interrupted"));
                } catch (ExecutionException e) {
                    results.add(MutationResult.of(mutant, MutationStatus.RUN_ERROR, 0, String.valueOf(e.getCause())));
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private MutationResult evaluate(MutationDescriptor mutant, MutationConfiguration configuration, CoverageData coverage) {
        MutationResult result;
        if (configuration.equivalentMutants().contains(mutant.id())) {
            result = MutationResult.of(mutant, MutationStatus.EQUIVALENT, 0, "Marked as equivalent; not run");
        } else if (coverage.available() && !coverage.isCovered(mutant.relativePath(), mutant.line())) {
            result = MutationResult.of(mutant, MutationStatus.NO_COVERAGE, 0,
                    "No test executed line " + mutant.line() + " in the baseline run");
        } else {
            try {
                result = executor.execute(mutant, configuration);
            } catch (RuntimeException e) {
                // A broken mutant must never abort the whole analysis.
                result = MutationResult.of(mutant, MutationStatus.RUN_ERROR, 0, e.toString());
            }
        }
        listener.mutantFinished(result);
        return result;
    }
}
