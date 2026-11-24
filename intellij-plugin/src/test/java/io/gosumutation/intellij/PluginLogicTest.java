package io.gosumutation.intellij;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLogicTest {

    @Test
    void commandRunsTheClassCommandWithAllInputs() {
        List<String> command = MutationCommand.build(new MutationCommand.Request(Path.of("/jdk"), Path.of("/cli/lib"),
                Path.of("/src/Foo.gs"), Path.of("/test/FooTest.gs"), Path.of("/trials/Foo"), Path.of("/stubs"),
                "--threads 2 --classpath \"/a b/x.jar\""));

        assertEquals(Path.of("/jdk", "bin", File.separatorChar == '\\' ? "java.exe" : "java").toString(), command.get(0));
        assertEquals(List.of("-cp", "/cli/lib" + File.separator + "*", "io.gosumutation.cli.Main", "class",
                        "--source", Path.of("/src/Foo.gs").toString(), "--trial-dir", Path.of("/trials/Foo").toString(),
                        "--test", Path.of("/test/FooTest.gs").toString(), "--stubs-dir", Path.of("/stubs").toString(),
                        "--threads", "2", "--classpath", "/a b/x.jar"),
                command.subList(1, command.size()).stream().map(s -> s.replace(Path.of("/cli/lib").toString(), "/cli/lib")).toList());
    }

    @Test
    void optionalInputsAreLeftOut() {
        List<String> command = MutationCommand.build(new MutationCommand.Request(Path.of("/jdk"), Path.of("/lib"),
                Path.of("/Foo.gs"), null, Path.of("/t"), null, ""));

        assertFalse(command.contains("--test"));
        assertFalse(command.contains("--stubs-dir"));
        assertEquals("--trial-dir", command.get(command.size() - 2));
    }

    @Test
    void trialPathsUseOneFolderPerClass() {
        TrialPaths paths = TrialPaths.of(Path.of("/code/a/Foo.gs"), "/trials", "");

        assertEquals(Path.of("/trials/Foo").toAbsolutePath(), paths.trialDir());
        assertEquals(paths.trialDir().resolve("src/stubs/gosu"), paths.stubsDir());
        assertFalse(paths.stubsDirConfigured());
        assertEquals(paths.trialDir().resolve("build/reports/gosu-mutation/mutation-report.html"), paths.htmlReport());
        assertTrue(TrialPaths.of(Path.of("/code/a/Foo.gs"), "/trials", " /shared/stubs ").stubsDirConfigured());
    }

    @Test
    void unresolvedNamesAreCollectedOncePerName() {
        String failure = """
                Baseline failed: the original project does not compile (FAILED)
                Foo.gs:[3,6] error: No type actions found in package com.example.feature [line:3 col:6] in
                Foo.gs:[4,6] error: "com" is not a valid type. [line:4 col:6] in
                Foo.gs:[16,29] error: "BaseAction" is not a valid type. [line:16 col:29] in
                Foo.gs:[17,29] error: "BaseAction" is not a valid type. [line:17 col:29] in
                Foo.gs:[56,30] error: Could not resolve symbol for : Status [line:56 col:30] in
                FooTest.gs:[23,5] error: No function defined for assertEquals. [line:23 col:5] in
                """;

        assertTrue(BaselineErrors.isCompilationFailure(failure));
        assertEquals(List.of("function assertEquals", "package com.example.feature.actions", "symbol Status",
                "type or package BaseAction", "type or package com"), BaselineErrors.unresolvedNames(failure));
    }

    @Test
    void stubRequestContainsTaskErrorsSourcesAndGuide() {
        String request = StubRequestBuilder.build(new StubRequestBuilder.Input(Path.of("/code/Foo.gs"), "package a\nclass Foo {}\n",
                Path.of("/code/FooTest.gs"), "package a\nclass FooTest {}\n",
                "does not compile\nerror: \"Bar\" is not a valid type.", Path.of("/stubs"), List.of("a/Old.gs"),
                "# Stubbing guide\nRules..."));

        assertTrue(request.contains("`" + Path.of("/stubs") + "`"), request);
        assertTrue(request.contains("Do **not** modify the class under test or its test"));
        assertTrue(request.contains("- type or package Bar"));
        assertTrue(request.contains("class Foo {}"));
        assertTrue(request.contains("class FooTest {}"));
        assertTrue(request.contains("- `a/Old.gs`"));
        assertTrue(request.contains("# Stubbing guide"));
    }

    @Test
    void longCompilerOutputIsTruncated() {
        String failure = "line\n".repeat(StubRequestBuilder.MAX_FAILURE_LINES + 50);
        String request = StubRequestBuilder.build(new StubRequestBuilder.Input(Path.of("/Foo.gs"), "class Foo {}", null, null,
                failure, Path.of("/s"), List.of(), "guide"));

        assertTrue(request.contains("(50 more lines)"));
        assertFalse(request.contains("## Test:"));
    }

    @Test
    void stubbingGuideIsBundled() {
        assertTrue(StubRequest.guide().startsWith("# Stubbing guide"), "processResources must copy docs/stubbing-guide.md");
    }
}
