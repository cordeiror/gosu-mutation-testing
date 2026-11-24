package io.gosumutation.gosu;

import gw.lang.parser.IParsedElement;
import gw.lang.parser.expressions.IAnnotationExpression;
import gw.lang.reflect.gs.IGosuClass;
import io.gosumutation.core.MutationConfiguration;
import io.gosumutation.core.MutationDescriptor;
import io.gosumutation.core.port.MutationGenerator;
import io.gosumutation.core.process.ForkedJvm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates mutants for Gosu classes: parses every selected {@code .gs} file with the Gosu parser,
 * offers each syntax tree element to the enabled operators and turns their edits into
 * {@link MutationDescriptor}s. Source files are only read.
 */
public final class GosuMutationGenerator implements MutationGenerator {

    private final List<GosuMutationOperator> operators;

    public GosuMutationGenerator() {
        this(GosuOperators.available());
    }

    public GosuMutationGenerator(List<GosuMutationOperator> operators) {
        this.operators = List.copyOf(operators);
    }

    @Override
    public List<MutationDescriptor> generate(MutationConfiguration configuration) {
        List<GosuMutationOperator> enabled = operators.stream()
                .filter(operator -> configuration.enablesOperator(operator.id()))
                .toList();

        List<Path> roots = new ArrayList<>(configuration.sourceDirs());
        roots.addAll(configuration.testDirs());
        List<Path> classpath = new ArrayList<>(ForkedJvm.frameworkClasspath());
        classpath.addAll(configuration.classpath());

        Map<String, MutationDescriptor> mutants = new LinkedHashMap<>();
        try (GosuSourceAnalyzer analyzer = GosuSourceAnalyzer.open(roots, classpath)) {
            for (Path sourceRoot : configuration.sourceDirs()) {
                for (Path file : GosuSourceFiles.classFiles(sourceRoot)) {
                    String relativePath = GosuSourceFiles.relativePath(sourceRoot, file);
                    String typeName = GosuSourceFiles.typeName(relativePath);
                    if (configuration.selectsClass(typeName)) {
                        mutateClass(analyzer, enabled, sourceRoot, file, relativePath, typeName, mutants);
                    }
                }
            }
        }
        return List.copyOf(mutants.values());
    }

    private void mutateClass(GosuSourceAnalyzer analyzer, List<GosuMutationOperator> enabled, Path sourceRoot,
                             Path file, String relativePath, String typeName, Map<String, MutationDescriptor> mutants) {
        String fileContent = read(file);
        GosuSourceAnalyzer.ParsedClass parsed = analyzer.parse(typeName);
        GosuSource source = new GosuSource(parsed.source());
        int[] fileOffsets = fileOffsets(parsed.source(), fileContent);
        if (fileOffsets == null) {
            throw new GosuAnalysisException("The text the Gosu parser read for " + typeName
                    + " does not match the content of " + file + "; refusing to compute mutations for it");
        }

        parsed.forEachElement(element -> {
            if (element.isSynthetic() || element.findAncestorParsedElementByType(IAnnotationExpression.class) != null) {
                return;
            }
            for (GosuMutationOperator operator : enabled) {
                for (SourceEdit edit : operator.mutate(element, source)) {
                    if (edit.start() < 0 || edit.end() <= edit.start() || edit.end() > parsed.source().length()) {
                        continue;
                    }
                    // Parser offsets are translated to offsets in the file as it is on disk.
                    int start = fileOffsets[edit.start()];
                    int end = fileOffsets[edit.end() - 1] + 1;
                    String original = fileContent.substring(start, end);
                    String id = MutationDescriptor.computeId(relativePath, operator.id(), start, original, edit.replacement());
                    String mutatedContent = fileContent.substring(0, start) + edit.replacement() + fileContent.substring(end);
                    mutants.putIfAbsent(id, new MutationDescriptor(
                            id,
                            operator.id(),
                            sourceRoot.toString(),
                            relativePath,
                            classNameOf(element, typeName),
                            methodNameOf(element),
                            source.lineOf(edit.start()),
                            source.columnOf(edit.start()),
                            start,
                            end,
                            original,
                            edit.replacement(),
                            edit.description(),
                            GosuSource.lineAt(fileContent, start),
                            GosuSource.lineAt(mutatedContent, start)));
                }
            }
        });
    }

    /**
     * Maps each offset of the text the parser saw to the offset of the same character in the file.
     * The Gosu parser drops carriage returns, so for a file with Windows line endings the two
     * differ. Differences in trailing whitespace are tolerated, since no mutation can be there.
     *
     * @return {@code null} when the texts differ in anything else
     */
    static int[] fileOffsets(String parserText, String fileContent) {
        int[] offsets = new int[parserText.length()];
        int filePosition = 0;
        for (int i = 0; i < parserText.length(); i++) {
            char expected = parserText.charAt(i);
            while (filePosition < fileContent.length() && fileContent.charAt(filePosition) != expected
                    && fileContent.charAt(filePosition) == '\r') {
                filePosition++;
            }
            if (filePosition < fileContent.length() && fileContent.charAt(filePosition) == expected) {
                offsets[i] = filePosition++;
            } else if (parserText.substring(i).isBlank() && fileContent.substring(filePosition).isBlank()) {
                java.util.Arrays.fill(offsets, i, offsets.length, fileContent.length());
                return offsets;
            } else {
                return null;
            }
        }
        return fileContent.substring(filePosition).isBlank() ? offsets : null;
    }

    private static String classNameOf(IParsedElement element, String fallback) {
        IGosuClass gosuClass = element.getGosuClass();
        return gosuClass == null ? fallback : gosuClass.getName();
    }

    private static String methodNameOf(IParsedElement element) {
        String name = element.getFunctionName();
        if (name == null || name.isBlank()) {
            return "<class>";
        }
        // Gosu names property accessors "@Name".
        return name.startsWith("@") ? "property " + name.substring(1) : name;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
