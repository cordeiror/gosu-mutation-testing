package io.gosumutation.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFileLocatorTest {

    @TempDir
    Path root;

    private Path write(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package a.b\n");
        return file;
    }

    @Test
    void mapsSourceFolderToTestFolderAndAddsTestSuffix() throws IOException {
        Path source = write("module/src/main/gosu/a/b/Foo.gs");
        Path test = write("module/src/test/gosu/a/b/FooTest.gs");

        assertEquals(Optional.of(test), TestFileLocator.find(source, "src/main/gosu=src/test/gosu"));
    }

    @Test
    void triesEveryMappingInOrder() throws IOException {
        Path source = write("module/code/a/b/Foo.gs");
        Path test = write("module/tests/a/b/FooTest.gs");

        assertEquals(Optional.of(test), TestFileLocator.find(source, "src/main/gosu=src/test/gosu; code=tests"));
    }

    @Test
    void fallsBackToTheSameFolder() throws IOException {
        Path source = write("module/a/Foo.gs");
        Path test = write("module/a/FooTest.gs");

        assertEquals(Optional.of(test), TestFileLocator.find(source, ""));
    }

    @Test
    void emptyWhenNothingExists() throws IOException {
        assertTrue(TestFileLocator.find(write("module/src/main/gosu/Foo.gs"), "src/main/gosu=src/test/gosu").isEmpty());
    }

    @Test
    void ignoresMalformedMappingsAndWindowsSeparators() {
        assertEquals(List.of("a=b", "c\\d=e"), TestFileLocator.parse("a=b;=x;y=;nothing\n c\\d=e "));
        Path source = Path.of("C:/w/c/d/p/Foo.gs");
        assertTrue(TestFileLocator.candidates(source, "c\\d=e").get(0).toString().replace('\\', '/').endsWith("/w/e/p/FooTest.gs"));
    }
}
