package io.gosumutation.gosu;

import gw.lang.gosuc.simple.GosuCompiler;
import gw.lang.parser.IParseTree;
import gw.lang.parser.IParsedElement;
import gw.lang.parser.statements.IClassStatement;
import gw.lang.reflect.IType;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.gs.IGosuClass;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Parses Gosu classes with the real Gosu parser (the public {@code gw.lang.parser} API) and exposes
 * their syntax tree.
 *
 * <p>The Gosu type system is a JVM wide singleton, so only one analyzer can be open at a time; a
 * second {@link #open} blocks until the first analyzer is closed. Nothing is compiled or written to
 * the analyzed source directories.
 */
public final class GosuSourceAnalyzer implements AutoCloseable {

    private static final ReentrantLock TYPE_SYSTEM_LOCK = new ReentrantLock();

    private final GosuCompiler gosu;
    private final Path scratchDir;

    private GosuSourceAnalyzer(GosuCompiler gosu, Path scratchDir) {
        this.gosu = gosu;
        this.scratchDir = scratchDir;
    }

    /**
     * @param sourceRoots directories whose types must be resolvable
     * @param classpath   jars/directories the sources depend on; must include the Gosu runtime
     */
    public static GosuSourceAnalyzer open(List<Path> sourceRoots, List<Path> classpath) {
        TYPE_SYSTEM_LOCK.lock();
        try {
            Path scratch = Files.createTempDirectory("gosu-mutation-analysis");
            GosuCompiler gosu = new GosuCompiler();
            gosu.initializeGosu(
                    sourceRoots.stream().map(Path::toString).toList(),
                    classpath.stream().map(Path::toString).toList(),
                    List.of(),
                    scratch.toString());
            return new GosuSourceAnalyzer(gosu, scratch);
        } catch (IOException e) {
            TYPE_SYSTEM_LOCK.unlock();
            throw new UncheckedIOException(e);
        } catch (RuntimeException | Error e) {
            TYPE_SYSTEM_LOCK.unlock();
            throw e;
        }
    }

    /** A parsed class: the text the parser saw and the root of its syntax tree. */
    public record ParsedClass(String typeName, String source, IClassStatement classStatement) {

        /** Visits every element of the tree, parents before children, in source order. */
        public void forEachElement(Consumer<IParsedElement> visitor) {
            IParseTree root = classStatement.getLocation();
            if (root != null) {
                walk(root, visitor);
            }
        }

        private static void walk(IParseTree node, Consumer<IParsedElement> visitor) {
            IParsedElement element = node.getParsedElement();
            if (element != null) {
                visitor.accept(element);
            }
            for (IParseTree child : node.getChildren()) {
                walk(child, visitor);
            }
        }
    }

    /**
     * @throws GosuAnalysisException when the type does not exist, is not a Gosu class or has errors
     */
    public ParsedClass parse(String typeName) {
        IType type;
        try {
            type = TypeSystem.getByFullNameIfValid(typeName);
        } catch (RuntimeException e) {
            throw new GosuAnalysisException("Could not load Gosu type " + typeName, e);
        }
        if (!(type instanceof IGosuClass gosuClass)) {
            throw new GosuAnalysisException(typeName + " is not a Gosu class in the configured source directories");
        }
        if (!gosuClass.isValid()) {
            throw new GosuAnalysisException("Gosu class " + typeName + " has errors: "
                    + gosuClass.getParseResultsException());
        }
        return new ParsedClass(typeName, gosuClass.getSource(), gosuClass.getClassStatement());
    }

    @Override
    public void close() {
        try {
            gosu.uninitializeGosu();
        } finally {
            deleteQuietly(scratchDir);
            TYPE_SYSTEM_LOCK.unlock();
        }
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // best effort: it is an empty temporary directory
        }
    }
}
