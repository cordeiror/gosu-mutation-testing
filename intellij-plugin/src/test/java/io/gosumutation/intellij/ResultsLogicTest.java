package io.gosumutation.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultsLogicTest {

    @TempDir
    Path temp;

    static final String REPORT = """
            {"schemaVersion":1,"generatedAt":"2026-11-25T18:44:13.985Z","projectDir":"D:\\trials\\Foo",
             "score":{"total":3,"killed":1,"survived":1,"equivalent":0,"mutationScore":50.0},
             "results":[
              {"mutation":{"id":"aaa111","operatorId":"EQUALITY","className":"demo.Foo","methodName":"cancel",
                "relativePath":"demo/Foo.gs","line":5,"column":27,"description":"replaced == with !=",
                "originalLine":"if (a == b) {","mutatedLine":"if (a != b) {"},
               "status":"KILLED","killingTests":["demo.FooTest.testCancel()"],"durationMillis":6694,"details":""},
              {"mutation":{"id":"bbb222","operatorId":"LOGICAL","className":"demo.Foo","methodName":"modify",
                "relativePath":"demo/Foo.gs","line":9,"column":3,"description":"replaced and with or",
                "originalLine":"if (x and not y) {","mutatedLine":"if (x or not y) {"},
               "status":"SURVIVED","killingTests":[],"durationMillis":1.5e3,"details":"tab\\tquote\\\" \\u00e9"},
              {"mutation":{"id":"ccc333","operatorId":"STRING_RETURN","className":"demo.Foo","methodName":"other",
                "relativePath":"demo/Foo.gs","line":12,"column":5,"description":"replaced String return value with \\\"\\\"",
                "originalLine":"return \\\"A\\\"","mutatedLine":"return \\\"\\\""},
               "status":"NO_COVERAGE","killingTests":[],"durationMillis":0,"details":null}
             ]}
            """;

    private MutationResults results() throws Exception {
        return MutationResults.read(Files.writeString(temp.resolve("mutation-report.json"), REPORT));
    }

    @Test
    void jsonReaderHandlesTheReportFormat() {
        Object parsed = Json.parse("{\"a\": [1, -2.5e1, true, false, null, \"x\\ny\"], \"b\": {}}");
        Map<?, ?> map = (Map<?, ?>) parsed;
        assertEquals(List.of(1.0, -25.0, true, false, "x\ny").subList(0, 4), ((List<?>) map.get("a")).subList(0, 4));
        assertNull(((List<?>) map.get("a")).get(4));
        assertEquals("x\ny", ((List<?>) map.get("a")).get(5));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1] 2"));
    }

    @Test
    void readsMutantsScoreAndSummary() throws Exception {
        MutationResults results = results();
        assertEquals(3, results.mutants().size());
        MutationResults.Mutant survivor = results.mutants().get(1);
        assertEquals("bbb222", survivor.id());
        assertEquals(9, survivor.line());
        assertEquals("tab\tquote\" \u00e9", survivor.details());
        assertEquals(List.of("demo.FooTest.testCancel()"), results.mutants().get(0).killingTests());
        assertEquals("", results.mutants().get(2).details());
        assertEquals(50.0, results.score());
        assertEquals(List.of("bbb222", "ccc333"), results.undetected().stream().map(MutationResults.Mutant::id).toList());
        assertEquals("50.0% · 1 killed · 1 survived · 1 no coverage · 3 mutants", results.summary());
    }

    @Test
    void markingAsEquivalentChangesStatusAndScoreAtOnce() throws Exception {
        MutationResults marked = results().withEquivalent(Set.of("bbb222"));
        assertEquals(MutationResults.EQUIVALENT, marked.mutants().get(1).status());
        assertEquals(MutationResults.SURVIVED, marked.mutants().get(1).reportStatus());
        assertEquals(100.0, marked.score());
        assertEquals(List.of("ccc333"), marked.undetected().stream().map(MutationResults.Mutant::id).toList());

        // A mutant the report has as EQUIVALENT that is no longer marked needs a new run.
        MutationResults reported = new MutationResults(null, "", List.of(new MutationResults.Mutant("x", "EQUIVALENT",
                "EQUIVALENT", "OP", "C", "m", "C.gs", 1, 1, "d", "a", "b", List.of(), "")));
        assertEquals(MutationResults.RUN_AGAIN, reported.withEquivalent(Set.of()).mutants().get(0).status());
        assertNull(reported.withEquivalent(Set.of()).score());
    }

    @Test
    void equivalentFileKeepsCommentsAndIgnoresDuplicates() throws Exception {
        Path file = temp.resolve("trial").resolve(EquivalentMutantsFile.FILE_NAME);
        MutationResults.Mutant survivor = results().mutants().get(1);
        assertTrue(EquivalentMutantsFile.read(file).isEmpty());

        EquivalentMutantsFile.add(file, survivor);
        EquivalentMutantsFile.add(file, survivor);
        EquivalentMutantsFile.add(file, results().mutants().get(2));

        assertEquals(Set.of("bbb222", "ccc333"), EquivalentMutantsFile.read(file));
        String text = Files.readString(file);
        assertTrue(text.startsWith("# Mutants marked as equivalent"), text);
        assertTrue(text.contains("bbb222  # demo.Foo line 9: replaced and with or"), text);
        assertEquals(1, text.lines().filter(l -> l.startsWith("bbb222")).count());

        EquivalentMutantsFile.remove(file, "bbb222");
        assertEquals(Set.of("ccc333"), EquivalentMutantsFile.read(file));
        assertEquals(EquivalentMutantsFile.of(temp.resolve("trial")), TrialPaths.of(temp.resolve("Foo.gs"),
                temp.toString(), "").trialDir().resolveSibling("trial").resolve(EquivalentMutantsFile.FILE_NAME));
    }

    @Test
    void testRequestListsUndetectedMutantsWithNumberedSource() throws Exception {
        Path source = temp.resolve("Foo.gs");
        String sourceText = "\npackage demo\n\nclass Foo {}\n";
        String request = TestRequestBuilder.build(new TestRequestBuilder.Input(source, sourceText, results().undetected(),
                List.of(new StubRequestBuilder.SourceFile(temp.resolve("FooTest.gs"), "class FooTest {}"))));

        assertTrue(request.startsWith("# Write tests that detect the surviving mutants of `Foo.gs`"), request);
        assertTrue(request.contains("### 1. Line 9, `modify`: replaced and with or"), request);
        assertTrue(request.contains("```diff\n- if (x and not y) {\n+ if (x or not y) {\n```"), request);
        assertTrue(request.contains("no test executes this line"), request);
        assertTrue(request.contains("Mark as Equivalent"), request);
        assertTrue(request.contains("2| package demo"), "line numbers must match the file: " + request);
        assertTrue(request.contains("class FooTest {}"), request);
        assertTrue(request.contains("existing test class below"), request);
        assertFalse(request.contains("aaa111"), "killed mutants are not listed");

        String noTests = TestRequestBuilder.build(new TestRequestBuilder.Input(source, sourceText, results().undetected(), null));
        assertTrue(noTests.contains("create a test class `FooTest.gs`"), noTests);
    }
}
