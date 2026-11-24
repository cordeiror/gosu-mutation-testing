package io.gosumutation.runner;

import io.gosumutation.core.port.CoverageData;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/** Line coverage read from a JaCoCo execution data file. */
public final class JacocoCoverage implements CoverageData {

    /** Lines that contain code, per source file ({@code demo/Foo.gs}). */
    private final Map<String, BitSet> codeLines = new HashMap<>();
    /** Lines that were executed at least partially. */
    private final Map<String, BitSet> coveredLines = new HashMap<>();

    private JacocoCoverage() { }

    public static JacocoCoverage load(Path execFile, Path classesDir) throws IOException {
        ExecFileLoader loader = new ExecFileLoader();
        loader.load(execFile.toFile());
        CoverageBuilder builder = new CoverageBuilder();
        new Analyzer(loader.getExecutionDataStore(), builder).analyzeAll(classesDir.toFile());

        JacocoCoverage coverage = new JacocoCoverage();
        for (ISourceFileCoverage file : builder.getSourceFiles()) {
            String key = file.getPackageName().isEmpty() ? file.getName() : file.getPackageName() + "/" + file.getName();
            BitSet code = coverage.codeLines.computeIfAbsent(key, k -> new BitSet());
            BitSet covered = coverage.coveredLines.computeIfAbsent(key, k -> new BitSet());
            for (int line = Math.max(1, file.getFirstLine()); line <= file.getLastLine(); line++) {
                int status = file.getLine(line).getStatus();
                if (status != ICounter.EMPTY) {
                    code.set(line);
                    if (status != ICounter.NOT_COVERED) {
                        covered.set(line);
                    }
                }
            }
        }
        return coverage;
    }

    @Override
    public boolean available() {
        return true;
    }

    /**
     * Conservative on purpose: a mutant is only skipped when its line is known to contain code that
     * was never executed. Lines without debug information (for example the continuation of a multi
     * line expression) count as covered, so their mutants are still run.
     */
    @Override
    public boolean isCovered(String relativeSourcePath, int line) {
        BitSet code = codeLines.get(relativeSourcePath);
        if (code == null || !code.get(line)) {
            return true;
        }
        return coveredLines.get(relativeSourcePath).get(line);
    }
}
