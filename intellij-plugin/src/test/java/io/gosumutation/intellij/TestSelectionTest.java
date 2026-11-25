package io.gosumutation.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSelectionTest {

    @TempDir
    Path root;

    private Path file(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "package a\n");
    }

    @Test
    void rememberedChoiceWinsOverTheMappedTest() throws IOException {
        Path source = file("code/gosu/a/Foo.gs");
        file("tests/gosu/a/FooTest.gs");
        Path folder = Files.createDirectories(root.resolve("tests/gosu/a/foo-cases"));
        Path extra = file("tests/gosu/a/FooExtraTest.gs");
        Map<String, String> chosen = new TreeMap<>();

        TestSelection.remember(chosen, source, List.of(folder, extra));

        assertEquals(List.of(folder, extra), TestSelection.resolve(source, chosen, "code/gosu=tests/gosu"));
        assertEquals("1 folder, 1 file", TestSelection.describe(List.of(folder, extra)));
    }

    @Test
    void fallsBackToTheMappedTestWhenTheRememberedChoiceIsGone() throws IOException {
        Path source = file("code/gosu/a/Foo.gs");
        Path mapped = file("tests/gosu/a/FooTest.gs");
        Map<String, String> chosen = new TreeMap<>();
        TestSelection.remember(chosen, source, List.of(root.resolve("deleted-folder")));

        assertEquals(List.of(mapped), TestSelection.resolve(source, chosen, "code/gosu=tests/gosu"));
    }

    @Test
    void emptyMeansAskAndForgettingRemovesTheEntry() throws IOException {
        Path source = file("code/gosu/a/Foo.gs");
        Map<String, String> chosen = new TreeMap<>();
        TestSelection.remember(chosen, source, List.of(source));
        TestSelection.remember(chosen, source, List.of());

        assertTrue(chosen.isEmpty());
        assertTrue(TestSelection.resolve(source, chosen, "code/gosu=tests/gosu").isEmpty());
    }

    @Test
    void chooserStartsInTheMappedTestFolderOrItsNearestExistingParent() throws IOException {
        Path source = file("module/code/gosu/a/b/Foo.gs");
        Path tests = Files.createDirectories(root.resolve("module/tests/gosu/a"));

        assertEquals(tests, TestFileLocator.suggestedFolder(source, "code/gosu=tests/gosu"));
        assertEquals(source.getParent(), TestFileLocator.suggestedFolder(source, "nothing=matches"));
    }
}
