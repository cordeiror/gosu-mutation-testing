package io.gosumutation.gosu;

import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.MutationDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real Gosu parser: nothing here is mocked. */
class GosuMutationGeneratorTest {

    private static final String SAMPLE = """
            package demo

            /** doc with >= and true */
            class Sample {
              var _limit : int = 10

              function check(a : int, b : int, flag : boolean) : boolean {
                // comment >= true or false 42
                var s = "a >= b and true 7"
                if (a <= b or flag == true) {
                  return a > 3 and not flag
                }
                return false
              }

              property get Limit() : int { return _limit + 5 }

              function ratio(x : double) : boolean { return x != 1.5 /* == */ && x < 9 }
            }
            """;

    @TempDir
    Path projectDir;
    Path sourceDir;
    Path sampleFile;

    @BeforeEach
    void writeSample() throws IOException {
        sourceDir = projectDir.resolve("src/main/gosu");
        sampleFile = sourceDir.resolve("demo/Sample.gs");
        Files.createDirectories(sampleFile.getParent());
        Files.writeString(sampleFile, SAMPLE);
    }

    private MutationConfiguration configuration(Set<String> operators, List<String> classes) {
        return new MutationConfiguration(projectDir, List.of(sourceDir), List.of(), List.of(), Duration.ofSeconds(30),
                operators, classes, projectDir.resolve("report"), 1);
    }

    private List<MutationDescriptor> generate(String... operators) {
        return new GosuMutationGenerator().generate(configuration(Set.of(operators), List.of()));
    }

    private static List<String> changes(List<MutationDescriptor> mutants) {
        return mutants.stream()
                .map(m -> m.line() + ":" + m.originalText() + "->" + m.replacementText())
                .sorted()
                .toList();
    }

    @Test
    void relationalBoundaries() {
        assertEquals(List.of("10:<=-><", "11:>->>=", "18:<-><="), changes(generate("RELATIONAL_BOUNDARY")));
    }

    @Test
    void equalityIgnoresOperatorsInsideComments() {
        List<MutationDescriptor> mutants = generate("EQUALITY");
        assertEquals(List.of("10:==->!=", "18:!=->=="), changes(mutants));
        MutationDescriptor notEquals = mutants.stream().filter(m -> m.line() == 18).findFirst().orElseThrow();
        assertEquals("function ratio(x : double) : boolean { return x == 1.5 /* == */ && x < 9 }", notEquals.mutatedLine());
    }

    @Test
    void logicalConnectivesInBothSpellings() {
        assertEquals(List.of("10:or->and", "11:and->or", "18:&&->||"), changes(generate("LOGICAL")));
    }

    @Test
    void booleanLiteralsOutsideCommentsAndStrings() {
        assertEquals(List.of("10:true->false", "13:false->true"), changes(generate("BOOLEAN_LITERAL")));
    }

    @Test
    void numericLiteralsOutsideCommentsAndStrings() {
        assertEquals(List.of("11:3->4", "16:5->6", "18:1.5->2.5", "18:9->10", "5:10->11"), changes(generate("NUMERIC_LITERAL")));
    }

    @Test
    void booleanReturnsButNotLiteralOrNonBooleanReturns() {
        assertEquals(List.of(
                        "11:a > 3 and not flag->false", "11:a > 3 and not flag->true",
                        "18:x != 1.5 /* == */ && x < 9->false", "18:x != 1.5 /* == */ && x < 9->true"),
                changes(generate("BOOLEAN_RETURN")));
    }

    @Test
    void descriptorsCarryLocationAndApplyCleanly() throws IOException {
        MutationDescriptor mutant = generate("RELATIONAL_BOUNDARY").stream()
                .filter(m -> m.line() == 11).findFirst().orElseThrow();

        assertEquals("demo.Sample", mutant.className());
        assertEquals("check", mutant.methodName());
        assertEquals("demo/Sample.gs", mutant.relativePath());
        assertEquals(16, mutant.column());
        assertEquals("return a > 3 and not flag", mutant.originalLine());
        assertEquals("return a >= 3 and not flag", mutant.mutatedLine());
        assertTrue(mutant.applyTo(SAMPLE).contains("return a >= 3 and not flag"));
        assertEquals(SAMPLE, Files.readString(sampleFile), "generation must not modify the source file");
    }

    @Test
    void propertiesAndFieldInitializersAreNamed() {
        List<MutationDescriptor> mutants = generate("NUMERIC_LITERAL");
        assertEquals("property Limit", mutants.stream().filter(m -> m.line() == 16).findFirst().orElseThrow().methodName());
        assertFalse(mutants.stream().filter(m -> m.line() == 5).findFirst().orElseThrow().methodName().isBlank());
    }

    @Test
    void idsAreUniqueAndStableAcrossRuns() {
        List<MutationDescriptor> first = generate();
        List<MutationDescriptor> second = generate();
        assertEquals(first, second);
        assertEquals(first.size(), first.stream().map(MutationDescriptor::id).distinct().count());
        assertEquals(19, first.size());
    }

    @Test
    void windowsLineEndingsGiveTheSameMutantsAndArePreserved() throws IOException {
        List<String> expected = changes(generate());
        String crlf = SAMPLE.replace("\n", "\r\n");
        Files.writeString(sampleFile, crlf);

        List<MutationDescriptor> mutants = generate();

        assertEquals(expected, changes(mutants));
        MutationDescriptor mutant = mutants.stream()
                .filter(m -> m.line() == 11 && m.operatorId().equals("RELATIONAL_BOUNDARY")).findFirst().orElseThrow();
        assertEquals(16, mutant.column());
        assertEquals(crlf.replace("return a > 3 and", "return a >= 3 and"), mutant.applyTo(crlf));
    }

    @Test
    void classFilterSelectsBySimpleName() throws IOException {
        Files.writeString(sourceDir.resolve("demo/Other.gs"),
                "package demo\nclass Other {\n  function f() : boolean { return true }\n}\n");
        GosuMutationGenerator generator = new GosuMutationGenerator();

        assertTrue(generator.generate(configuration(Set.of(), List.of("Other"))).stream()
                .allMatch(m -> m.className().equals("demo.Other")));
        assertEquals(20, generator.generate(configuration(Set.of(), List.of())).size());
    }
}
