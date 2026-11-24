package io.gosumutation.gosu;

import io.gosumutation.core.port.ProjectCompiler.CompilationRequest;
import io.gosumutation.core.port.ProjectCompiler.CompilationResult;
import io.gosumutation.core.port.ProjectCompiler.CompilationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real Gosu compiler in a child JVM. */
class GosuCompilerAdapterTest {

    @TempDir
    Path tempDir;

    private CompilationResult compile(String source) throws IOException {
        // The space in the path is deliberate.
        Path sourceRoot = tempDir.resolve("my project/src");
        Files.createDirectories(sourceRoot.resolve("demo"));
        Files.writeString(sourceRoot.resolve("demo/Greeter.gs"), source);
        return new GosuCompilerAdapter().compile(new CompilationRequest(List.of(sourceRoot), List.of(),
                tempDir.resolve("my project/classes"), tempDir.resolve("my project/work"), Duration.ofMinutes(2)));
    }

    @Test
    void compilesValidGosuToClassFiles() throws IOException {
        CompilationResult result = compile("package demo\nclass Greeter {\n  function hi() : String { return \"hi\" }\n}\n");

        assertEquals(CompilationStatus.SUCCESS, result.status(), result.output());
        assertTrue(Files.exists(tempDir.resolve("my project/classes/demo/Greeter.class")));
    }

    @Test
    void reportsCompileErrorsAsFailedNotAsInfrastructureError() throws IOException {
        CompilationResult result = compile("package demo\nclass Greeter {\n  function hi() : int { return \"hi\" }\n}\n");

        assertEquals(CompilationStatus.FAILED, result.status(), result.output());
        assertTrue(result.output().contains("Greeter.gs"), result.output());
        assertFalse(Files.exists(tempDir.resolve("my project/classes/demo/Greeter.class")));
    }
}
