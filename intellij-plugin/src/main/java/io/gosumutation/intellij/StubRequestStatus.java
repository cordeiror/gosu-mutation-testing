package io.gosumutation.intellij;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Whether an AI stub request can be built for a class, and if not, why: the explanation shown
 * instead of a silently disabled menu item.
 */
record StubRequestStatus(boolean canBuild, String message) {

    static final int LOG_TAIL_LINES = 15;

    static StubRequestStatus of(TrialPaths paths, String classFileName) {
        if (Files.isRegularFile(paths.baselineFailure())) {
            return new StubRequestStatus(true, "");
        }
        if (Files.isRegularFile(paths.htmlReport())) {
            return new StubRequestStatus(false, "The last run of " + classFileName + " succeeded, so no stubs are needed.\n"
                    + "Report: " + paths.htmlReport() + "\n\n"
                    + "This action is for runs where the class or its tests do not compile.");
        }
        List<String> tail = tail(paths);
        if (!tail.isEmpty()) {
            return new StubRequestStatus(false, "The last run of " + classFileName + " stopped before compiling the class, "
                    + "so there are no compiler errors to send to an AI assistant.\n"
                    + "Fix the problem below (often the chosen tests: use \"Run Mutation Testing with Chosen Tests...\") "
                    + "and run again.\n\nLast lines of the run (" + paths.lastRunLog() + "):\n\n"
                    + String.join("\n", tail));
        }
        return new StubRequestStatus(false, classFileName + " has not been run yet in " + paths.trialDir() + ".\n\n"
                + "Run \"Run Mutation Testing\" first. If the class or its tests do not compile, this action then prepares "
                + "the request for an AI assistant.");
    }

    private static List<String> tail(TrialPaths paths) {
        try {
            if (!Files.isRegularFile(paths.lastRunLog())) {
                return List.of();
            }
            List<String> lines = Files.readString(paths.lastRunLog(), StandardCharsets.UTF_8).strip().lines()
                    .filter(line -> !line.isBlank()).toList();
            return lines.subList(Math.max(0, lines.size() - LOG_TAIL_LINES), lines.size());
        } catch (IOException e) {
            return List.of();
        }
    }
}
