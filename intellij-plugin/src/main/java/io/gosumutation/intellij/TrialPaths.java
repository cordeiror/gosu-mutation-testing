package io.gosumutation.intellij;

import java.nio.file.Path;

/** Where the plugin puts and finds things for one class. */
record TrialPaths(Path trialDir, Path stubsDir, boolean stubsDirConfigured) {

    /**
     * @param trialsRoot folder that holds one trial project per class
     * @param stubsDir   configured stubs folder, or blank for {@code <trial>/src/stubs/gosu}
     */
    static TrialPaths of(Path source, String trialsRoot, String stubsDir) {
        String fileName = source.getFileName().toString();
        String className = fileName.endsWith(".gs") ? fileName.substring(0, fileName.length() - 3) : fileName;
        Path trial = Path.of(trialsRoot).resolve(className).toAbsolutePath().normalize();
        boolean configured = stubsDir != null && !stubsDir.isBlank();
        Path stubs = configured ? Path.of(stubsDir.trim()).toAbsolutePath().normalize() : trial.resolve("src/stubs/gosu");
        return new TrialPaths(trial, stubs, configured);
    }

    Path reportDir() {
        return trialDir.resolve("build/reports/gosu-mutation");
    }

    Path htmlReport() {
        return reportDir().resolve("mutation-report.html");
    }

    /**
     * The report as a {@code file:} URI built by the JDK. Passing a {@link java.io.File} to the IDE's
     * browser helper on Windows produced URLs with escaped backslashes ({@code file:///D:/%5C...}).
     */
    java.net.URI htmlReportUri() {
        return htmlReport().toAbsolutePath().normalize().toUri();
    }

    Path baselineFailure() {
        return reportDir().resolve("baseline-failure.txt");
    }

    /** Console output of the last run started from the plugin. */
    Path lastRunLog() {
        return trialDir.resolve("build").resolve("last-run.log");
    }
}
