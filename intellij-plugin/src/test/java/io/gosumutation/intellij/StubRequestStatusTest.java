package io.gosumutation.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubRequestStatusTest {

    @TempDir
    Path trials;

    private TrialPaths paths() {
        return TrialPaths.of(Path.of("Foo.gs"), trials.toString(), "");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void notRunYetSaysSoAndWhereItLooked() {
        StubRequestStatus status = StubRequestStatus.of(paths(), "Foo.gs");

        assertFalse(status.canBuild());
        assertTrue(status.message().contains("has not been run yet"), status.message());
        assertTrue(status.message().contains(paths().trialDir().toString()), status.message());
    }

    @Test
    void aSuccessfulRunNeedsNoStubs() throws IOException {
        write(paths().htmlReport(), "<html>");

        StubRequestStatus status = StubRequestStatus.of(paths(), "Foo.gs");

        assertFalse(status.canBuild());
        assertTrue(status.message().contains("succeeded"), status.message());
    }

    @Test
    void aRunThatStoppedBeforeCompilingShowsTheLastLines() throws IOException {
        StringBuilder log = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            log.append("line ").append(i).append('\n');
        }
        log.append("Copied A.gs\nERROR: no Gosu tests found in /x/src/test/gosu.\n");
        write(paths().lastRunLog(), log.toString());

        StubRequestStatus status = StubRequestStatus.of(paths(), "Foo.gs");

        assertFalse(status.canBuild());
        assertTrue(status.message().contains("stopped before compiling"), status.message());
        assertTrue(status.message().contains("ERROR: no Gosu tests found"), status.message());
        assertFalse(status.message().contains("line 5\n"), "only the last lines are shown");
    }

    @Test
    void aBaselineFailureCanBeSent() throws IOException {
        write(paths().baselineFailure(), "Baseline failed: the original project does not compile");
        write(paths().lastRunLog(), "anything");

        assertTrue(StubRequestStatus.of(paths(), "Foo.gs").canBuild());
    }
}
