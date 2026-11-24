package io.gosumutation.core.process;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs a command in a separate operating system process with a hard timeout.
 *
 * <p>Output goes to a file instead of a pipe, so a chatty or hung child can never block the
 * framework, and on timeout the whole process tree is killed.
 */
public final class ProcessIsolation {

    private static final int MAX_OUTPUT_CHARS = 16_000;

    private ProcessIsolation() { }

    public record ProcessResult(int exitCode, boolean timedOut, String output, long durationMillis) { }

    public static ProcessResult run(List<String> command, Path workDir, Duration timeout) throws IOException {
        Files.createDirectories(workDir);
        Path log = Files.createTempFile(workDir, "process-", ".log");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        long start = System.nanoTime();
        Process process = builder.start();
        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + command.get(0), e);
        }
        if (!finished) {
            kill(process);
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        return new ProcessResult(finished ? process.exitValue() : -1, !finished, tail(log), millis);
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String tail(Path log) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(log.toFile(), "r")) {
            long length = file.length();
            long from = Math.max(0, length - MAX_OUTPUT_CHARS);
            byte[] bytes = new byte[(int) (length - from)];
            file.seek(from);
            file.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8).strip();
        }
    }
}
