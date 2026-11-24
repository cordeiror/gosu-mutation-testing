package io.gosumutation.intellij;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the names the Gosu compiler could not resolve out of a baseline failure. */
final class BaselineErrors {

    private static final Pattern NOT_A_TYPE = Pattern.compile("\"([\\w.$]+)\" is not a valid type");
    private static final Pattern NOT_IN_PACKAGE = Pattern.compile("No type (\\w+) found in package ([\\w.]+)");
    private static final Pattern UNRESOLVED = Pattern.compile("Could not resolve symbol for : (\\w+)");
    private static final Pattern NO_FUNCTION = Pattern.compile("No function defined for (\\w+)");

    private BaselineErrors() { }

    /** The baseline failed because the copied sources do not compile. */
    static boolean isCompilationFailure(String failure) {
        return failure != null && failure.contains("does not compile");
    }

    /** Types, packages and members the compiler could not find, sorted and without duplicates. */
    static List<String> unresolvedNames(String failure) {
        Set<String> names = new TreeSet<>();
        if (failure == null) {
            return List.of();
        }
        collect(NOT_A_TYPE.matcher(failure), m -> "type or package " + m.group(1), names);
        collect(NOT_IN_PACKAGE.matcher(failure), m -> "package " + m.group(2) + "." + m.group(1), names);
        collect(UNRESOLVED.matcher(failure), m -> "symbol " + m.group(1), names);
        collect(NO_FUNCTION.matcher(failure), m -> "function " + m.group(1), names);
        return List.copyOf(names);
    }

    private static void collect(Matcher matcher, java.util.function.Function<Matcher, String> label, Set<String> names) {
        while (matcher.find()) {
            names.add(label.apply(matcher));
        }
    }
}
