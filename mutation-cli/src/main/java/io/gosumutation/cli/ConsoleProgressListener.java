package io.gosumutation.cli;

import io.gosumutation.core.MutationDescriptor;
import io.gosumutation.core.MutationResult;
import io.gosumutation.core.port.BaselineResult;
import io.gosumutation.core.port.MutationListener;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Structured progress log: one line per event, written to standard error as
 * {@code time level event key=value ...} so it is easy to read and to parse.
 */
final class ConsoleProgressListener implements MutationListener {

    private final PrintStream out;
    private final AtomicInteger finished = new AtomicInteger();
    private volatile int total;

    ConsoleProgressListener(PrintStream out) {
        this.out = out;
    }

    private void log(String level, String event, String fields) {
        synchronized (out) {
            out.println(LocalTime.now().truncatedTo(ChronoUnit.MILLIS) + " " + level + " " + event + " " + fields);
        }
    }

    @Override
    public void baselineStarted() {
        log("INFO ", "baseline_started", "step=\"compile and test the original project\"");
    }

    @Override
    public void baselineFinished(BaselineResult baseline) {
        log(baseline.passed() ? "INFO " : "ERROR", "baseline_finished", "passed=" + baseline.passed()
                + " tests=" + baseline.testsRun() + " coverage=" + baseline.coverage().available()
                + " ms=" + baseline.durationMillis());
    }

    @Override
    public void mutantsGenerated(List<MutationDescriptor> mutants) {
        total = mutants.size();
        log("INFO ", "mutants_generated", "count=" + mutants.size());
    }

    @Override
    public void mutantFinished(MutationResult result) {
        MutationDescriptor m = result.mutation();
        log("INFO ", "mutant_finished", "n=" + finished.incrementAndGet() + "/" + total
                + " id=" + m.id() + " status=" + result.status() + " operator=" + m.operatorId()
                + " location=" + m.className() + ":" + m.line() + " ms=" + result.durationMillis());
    }
}
