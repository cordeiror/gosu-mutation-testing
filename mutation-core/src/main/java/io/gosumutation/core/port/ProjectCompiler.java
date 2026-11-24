package io.gosumutation.core.port;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Compiles a source tree. The Gosu implementation is {@code GosuCompilerAdapter}. */
public interface ProjectCompiler {

    CompilationResult compile(CompilationRequest request);

    /**
     * @param sourceRoots source directories (production and test)
     * @param classpath   project dependencies
     * @param outputDir   where class files are written
     * @param workDir     scratch directory owned by the caller
     * @param timeout     limit after which the compiler is killed
     */
    record CompilationRequest(List<Path> sourceRoots, List<Path> classpath, Path outputDir, Path workDir, Duration timeout) {
        public CompilationRequest {
            sourceRoots = List.copyOf(sourceRoots);
            classpath = List.copyOf(classpath);
        }
    }

    enum CompilationStatus { SUCCESS, FAILED, TIMED_OUT, ERROR }

    record CompilationResult(CompilationStatus status, String output, long durationMillis) { }
}
