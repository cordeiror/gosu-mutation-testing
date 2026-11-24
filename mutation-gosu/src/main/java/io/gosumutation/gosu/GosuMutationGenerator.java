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

        parsed.forEachElement(element -> {
            if (element.isSynthetic() || element.findAncestorParsedElementByType(IAnnotationExpression.class) != null) {
                return;
            }
            for (GosuMutationOperator operator : enabled) {
                for (SourceEdit edit : operator.mutate(element, source)) {
                    // The parser's offsets are only usable if they address the same text on disk.
                    if (edit.end() > fileContent.length()
                            || !fileContent.regionMatches(edit.start(), source.text(), edit.start(), edit.end() - edit.start())) {
                        continue;
                    }
                    String original = fileContent.substring(edit.start(), edit.end());
                    String id = MutationDescriptor.computeId(relativePath, operator.id(), edit.start(), original, edit.replacement());
                    String mutatedContent = fileContent.substring(0, edit.start()) + edit.replacement() + fileContent.substring(edit.end());
                    mutants.putIfAbsent(id, new MutationDescriptor(
                            id,
                            operator.id(),
                            sourceRoot.toString(),
                            relativePath,
                            classNameOf(element, typeName),
                            methodNameOf(element),
                            source.lineOf(edit.start()),
                            source.columnOf(edit.start()),
                            edit.start(),
                            edit.end(),
                            original,
                            edit.replacement(),
                            edit.description(),
                            GosuSource.lineAt(fileContent, edit.start()),
                            GosuSource.lineAt(mutatedContent, edit.start())));
                }
            }
        });
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
