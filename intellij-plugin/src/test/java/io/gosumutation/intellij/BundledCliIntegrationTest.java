package io.gosumutation.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real gosu-mutation jars with the exact command line the plugin builds. No mocks. */
class BundledCliIntegrationTest {

    private static final Path CLI_LIB = Path.of(System.getProperty("gosu.mutation.cli.lib"));
    private static final Path SAMPLE = Path.of(System.getProperty("gosu.mutation.sample"));
    private static final Path JAVA_HOME = Path.of(System.getProperty("java.home"));

    @TempDir
    Path temp;

    private int run(List<String> command, Path log) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(process.waitFor(5, TimeUnit.MINUTES), "gosu-mutation did not finish");
        return process.exitValue();
    }

    @Test
    void runsAClassWithItsTestAndProducesTheReport() throws Exception {
        Path source = SAMPLE.resolve("src/main/gosu/sample/EligibilityService.gs");
        Path test = SAMPLE.resolve("src/test/gosu/sample/EligibilityServiceTest.gs");
        Path testFound = TestFileLocator.find(source, "src/main/gosu=src/test/gosu").orElseThrow();
        assertEquals(test, testFound);
        TrialPaths paths = TrialPaths.of(source, temp.resolve("trials with space").toString(), "");
        Path log = temp.resolve("run.log");

        int exit = run(MutationCommand.build(new MutationCommand.Request(JAVA_HOME, CLI_LIB, source, List.of(testFound),
                paths.trialDir(), null, "--quiet --threads 2")), log);

        String output = Files.readString(log);
        assertEquals(0, exit, output);
        assertTrue(Files.isRegularFile(paths.htmlReport()), output);
        assertTrue(output.contains("Killed            : 5"), output);
    }

    @Test
    void compileFailureLeavesWhatTheAiRequestNeeds() throws Exception {
        Path code = temp.resolve("code/src/main/gosu/com/example/feature");
        Files.createDirectories(code);
        Path source = Files.writeString(code.resolve("Greeter.gs"), """
                package com.example.feature

                uses com.example.feature.actions.BaseAction

                class Greeter {
                  function kind(action : Type<BaseAction>) : String {
                    return Status.ACTIVE as String
                  }
                }
                """);
        Path testDir = temp.resolve("code/src/test/gosu/com/example/feature");
        Files.createDirectories(testDir);
        Path test = Files.writeString(testDir.resolve("GreeterTest.gs"), """
                package com.example.feature

                uses org.junit.jupiter.api.Test

                class GreeterTest {
                  @Test
                  function runs() {
                  }
                }
                """);
        TrialPaths paths = TrialPaths.of(source, temp.resolve("trials").toString(), "");

        int exit = run(MutationCommand.build(new MutationCommand.Request(JAVA_HOME, CLI_LIB, source, List.of(test),
                paths.trialDir(), null, "--quiet")), temp.resolve("run.log"));

        assertEquals(1, exit);
        String failure = Files.readString(paths.baselineFailure(), StandardCharsets.UTF_8);
        assertTrue(BaselineErrors.isCompilationFailure(failure), failure);
        List<String> unresolved = BaselineErrors.unresolvedNames(failure);
        assertTrue(unresolved.contains("package com.example.feature.actions"), unresolved.toString());
        assertTrue(unresolved.contains("symbol Status"), unresolved.toString());

        String request = StubRequestBuilder.build(new StubRequestBuilder.Input(source, Files.readString(source),
                StubRequest.testSources(List.of(test.getParent())), failure, paths.stubsDir(), List.of(), StubRequest.guide()));
        assertTrue(request.contains("- symbol Status"), request);
        assertTrue(request.contains(paths.stubsDir().toString()), request);
        assertTrue(request.contains("## Recipes"), "the guide is embedded");
    }

    @Test
    void runsAClassWithAFolderOfTestsChosenInTheChooser() throws Exception {
        Path source = SAMPLE.resolve("src/main/gosu/sample/EligibilityService.gs");
        Path folder = temp.resolve("module tests/sample");
        Files.createDirectories(folder.resolve("nested"));
        Files.writeString(folder.resolve("AgeTest.gs"), """
                package sample

                uses org.junit.jupiter.api.Assertions
                uses org.junit.jupiter.api.Test

                class AgeTest {
                  @Test
                  function boundary() {
                    Assertions.assertFalse(new EligibilityService().isEligible(17, true))
                    Assertions.assertTrue(new EligibilityService().isEligible(18, true))
                  }
                }
                """);
        Files.writeString(folder.resolve("nested/LicenseTest.gs"), """
                package sample

                uses org.junit.jupiter.api.Assertions
                uses org.junit.jupiter.api.Test

                class LicenseTest {
                  @Test
                  function licenseIsRequired() {
                    Assertions.assertFalse(new EligibilityService().isEligible(40, false))
                  }
                }
                """);
        TrialPaths paths = TrialPaths.of(source, temp.resolve("trials").toString(), "");
        Path log = temp.resolve("folder.log");

        int exit = run(MutationCommand.build(new MutationCommand.Request(JAVA_HOME, CLI_LIB, source,
                List.of(folder.getParent()), paths.trialDir(), null, "--threads 2")), log);

        String output = Files.readString(log);
        assertEquals(0, exit, output);
        assertTrue(output.contains("Copied 2 Gosu file(s) found under"), output);
        assertTrue(output.contains("Killed            : 5"), output);
        assertEquals(2, StubRequest.testSources(List.of(folder.getParent())).size());
    }

    @Test
    void cliLibUsesTheJarWildcard() {
        assertTrue(Files.isRegularFile(CLI_LIB.resolve("mutation-cli-0.1.0.jar")), "run :mutation-cli:installDist");
        assertTrue(MutationCommand.build(new MutationCommand.Request(JAVA_HOME, CLI_LIB, Path.of("A.gs"), List.of(),
                Path.of("t"), null, "")).contains(CLI_LIB + File.separator + "*"));
    }
}
