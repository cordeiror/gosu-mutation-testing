package io.gosumutation.gosu;

import io.gosumutation.core.port.ProjectCompiler;
import io.gosumutation.core.process.ForkedJvm;
import io.gosumutation.core.process.ProcessIsolation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compiles Gosu sources by running the open source Gosu command line compiler ({@code gosuc}) in a
 * child JVM. A separate process keeps the Gosu type system of each compilation fully isolated and
 * lets a stuck compilation be killed.
 */
public final class GosuCompilerAdapter implements ProjectCompiler {

    static final String GOSUC_MAIN = "gw.lang.gosuc.cli.CommandLineCompiler";
    static final int MAX_ERRORS = 100_000;

    @Override
    public CompilationResult compile(CompilationRequest request) {
        try {
            List<Path> classpath = new ArrayList<>(ForkedJvm.frameworkClasspath());
            classpath.addAll(request.classpath());

            List<String> gosucArgs = new ArrayList<>();
            gosucArgs.add("-sourcepath");
            gosucArgs.add(join(request.sourceRoots()));
            gosucArgs.add("-classpath");
            gosucArgs.add(join(classpath));
            gosucArgs.add("-d");
            gosucArgs.add(request.outputDir().toString());
            gosucArgs.add("-nowarn");
            // gosuc stops after 100 errors by default; report them all, so that whoever fixes them
            // (a person or an AI assistant) sees everything that is missing in one go.
            gosucArgs.add("-maxerrs");
            gosucArgs.add(String.valueOf(MAX_ERRORS));
            int options = gosucArgs.size();
            for (Path root : request.sourceRoots()) {
                GosuSourceFiles.compilableFiles(root).forEach(file -> gosucArgs.add(file.toString()));
            }
            if (gosucArgs.size() == options) {
                return new CompilationResult(CompilationStatus.ERROR, "No Gosu source files found in " + request.sourceRoots(), 0);
            }
            Files.createDirectories(request.outputDir());

            List<String> command = ForkedJvm.command(request.workDir(), List.of(), classpath, GOSUC_MAIN, gosucArgs);
            ProcessIsolation.ProcessResult result = ProcessIsolation.run(command, request.workDir(), request.timeout());

            CompilationStatus status;
            if (result.timedOut()) {
                status = CompilationStatus.TIMED_OUT;
            } else if (result.exitCode() == 0) {
                status = CompilationStatus.SUCCESS;
            } else if (result.output().contains("gosuc completed with")) {
                // gosuc ran to the end and reported compile errors.
                status = CompilationStatus.FAILED;
            } else {
                status = CompilationStatus.ERROR;
            }
            return new CompilationResult(status, result.output(), result.durationMillis());
        } catch (IOException | RuntimeException e) {
            return new CompilationResult(CompilationStatus.ERROR, e.toString(), 0);
        }
    }

    private static String join(List<Path> paths) {
        return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    }
}
