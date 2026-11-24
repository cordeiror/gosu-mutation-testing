package io.gosumutation.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything a mutation run needs to know about the project under test.
 *
 * @param projectDir       root of the project under test
 * @param sourceDirs       directories with production sources (absolute)
 * @param testDirs         directories with test sources (absolute)
 * @param classpath        extra jars/directories needed to compile and run the project
 * @param mutantTimeout    limit for compiling and for testing each mutant
 * @param enabledOperators operator ids to use; empty means every available operator
 * @param classFilters     simple or fully qualified class names ('*' wildcard allowed); empty means all
 * @param reportDir        where reports are written
 * @param parallelism      number of mutants evaluated concurrently
 */
public record MutationConfiguration(
        Path projectDir,
        List<Path> sourceDirs,
        List<Path> testDirs,
        List<Path> classpath,
        Duration mutantTimeout,
        Set<String> enabledOperators,
        List<String> classFilters,
        Path reportDir,
        int parallelism) {

    public MutationConfiguration {
        Objects.requireNonNull(projectDir, "projectDir");
        sourceDirs = List.copyOf(sourceDirs);
        testDirs = List.copyOf(testDirs);
        classpath = List.copyOf(classpath);
        enabledOperators = Set.copyOf(enabledOperators);
        classFilters = List.copyOf(classFilters);
        Objects.requireNonNull(mutantTimeout, "mutantTimeout");
        Objects.requireNonNull(reportDir, "reportDir");
        if (sourceDirs.isEmpty()) {
            throw new IllegalArgumentException("At least one source directory is required");
        }
        if (mutantTimeout.isZero() || mutantTimeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        if (parallelism < 1) {
            throw new IllegalArgumentException("Parallelism must be at least 1");
        }
    }

    /** Tells whether the class is selected by {@link #classFilters()}. */
    public boolean selectsClass(String fullyQualifiedName) {
        if (classFilters.isEmpty()) {
            return true;
        }
        String simpleName = fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
        for (String filter : classFilters) {
            String regex = ("\\Q" + filter + "\\E").replace("*", "\\E.*\\Q");
            if (fullyQualifiedName.matches(regex) || simpleName.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    /** Tells whether the operator is selected by {@link #enabledOperators()}. */
    public boolean enablesOperator(String operatorId) {
        return enabledOperators.isEmpty() || enabledOperators.contains(operatorId);
    }
}
