package io.gosumutation.intellij;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubWizardLogicTest {

    /** Shaped like a real failure: types missing, and the errors that only follow from them. */
    static final String FAILURE = """
            Baseline failed: the original project does not compile (FAILED)
            Utils.gs:[5,20] error: "Vehicle" is not a valid type. [line:5 col:20] in
            Utils.gs:[7,9] error: Could not resolve symbol for : current [line:7 col:9] in
            Utils.gs:[8,11] error: Could not resolve symbol for : _logger [line:8 col:11] in
            Utils.gs:[9,24] error: Could not resolve symbol for : vehicle [line:9 col:24] in
            Utils.gs:[10,69] error: Expecting ":" clause for function declaration. [line:10 col:69] in
            Utils.gs:[10,76] error: Could not resolve symbol for : opt [line:10 col:76] in
            Utils.gs:[11,33] error: Could not resolve symbol for : TermKind [line:11 col:33] in
            Utils.gs:[12,3] error: "OptionTerm" is not a valid type. [line:12 col:3] in
            Utils.gs:[13,3] error: No type actions found in package com.example [line:13 col:3] in
            gosuc completed with 388 errors. Warnings were disabled.
            """;

    static final String SOURCE = """
            package com.example
            class Utils {
              static var _logger = LoggerFactory.getLogger("x")
              static function f(vehicle : Vehicle) : int {
                var current = vehicle.Value
                return list.firstWhere(\\opt -> opt.Value == current)
              }
            }
            """;

    @Test
    void separatesMissingTypesFromErrorsThatFollowFromThem() {
        BaselineErrors.Missing missing = BaselineErrors.missing(FAILURE, List.of(SOURCE));

        assertEquals(List.of("OptionTerm", "Vehicle", "com.example.actions"), missing.types());
        assertEquals(List.of("TermKind"), missing.symbols(), "declared names are consequences, not missing types");
        assertEquals(388, BaselineErrors.errorCount(FAILURE));
        assertEquals(2, BaselineErrors.errorCount("Foo.gs:[1,1] error: a\nBar.gsx:[2,2] error: b"));
        assertEquals(-1, BaselineErrors.errorCount("Baseline failed: tests fail"));
    }

    @Test
    void checkOutcomes() {
        StubCheck compile = StubCheck.of(1, FAILURE, "", List.of(SOURCE));
        assertEquals(StubCheck.Kind.DOES_NOT_COMPILE, compile.kind());
        assertEquals(388, compile.errorCount());

        assertEquals(StubCheck.Kind.READY, StubCheck.of(0, null,
                "Baseline OK: the project compiles and its 7 tests pass.\n", List.of()).kind());
        assertEquals(StubCheck.Kind.TESTS_FAIL, StubCheck.of(1,
                "Baseline failed: 1 of 7 tests failed: demo.FooTest.x()", "", List.of()).kind());
        StubCheck other = StubCheck.of(2, null, "line 1\nERROR: No Gosu tests found in D:\\trial\n", List.of());
        assertEquals(StubCheck.Kind.OTHER, other.kind());
        assertTrue(other.message().contains("No Gosu tests found"), other.message());
    }

    @Test
    void resultPageSaysWhatIsMissingAndWhatToDoNext() {
        String page = StubWizardTexts.result("Utils.gs", StubCheck.of(1, FAILURE, "", List.of(SOURCE)),
                List.of(388, 42), Path.of("/stubs"));

        assertTrue(page.contains("does not compile yet: 388 errors"), page);
        assertTrue(page.contains("<code>OptionTerm</code>, <code>Vehicle</code>"), page);
        assertTrue(page.contains("TermKind"), page);
        assertFalse(page.contains("<code>_logger</code>"), page);
        assertTrue(page.contains("Copy AI Stub Request"), page);
        assertTrue(page.contains("388 &rarr; 42"), page);
        String ready = StubWizardTexts.result("Utils.gs", StubCheck.of(0, null,
                "Baseline OK: the project compiles and its 7 tests pass.\n", List.of()), List.of(388, 0), Path.of("/s"));
        assertTrue(ready.contains("Ready: you can now run mutation testing."), ready);
        assertTrue(ready.contains("Utils.gs compiles and its 7 tests pass in the trial project, so the stubs are complete."), ready);
        assertTrue(ready.contains("Press <b>Run Mutation Testing</b> below"), ready);
        assertTrue(ready.contains("388 &rarr; 0"), ready);
        assertTrue(StubWizardTexts.intro("A<B>.gs").contains("A&lt;B&gt;.gs"));
    }

    @Test
    void helpExplainsEveryMenuOption() throws Exception {
        String pluginXml = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"));
        Matcher text = Pattern.compile("<action id=\"GosuMutation\\.(?!Help)[^\"]+\"[^>]*?text=\"([^\"]+)\"",
                Pattern.DOTALL).matcher(pluginXml);
        int actions = 0;
        while (text.find()) {
            actions++;
            assertTrue(PluginHelp.html().contains("<b>" + text.group(1) + "</b>"), "help misses " + text.group(1));
        }
        assertEquals(6, actions);
    }
}
