package io.gosumutation.intellij;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads what the Gosu compiler could not find out of a baseline failure. */
final class BaselineErrors {

    private static final Pattern NOT_A_TYPE = Pattern.compile("\"([\\w.$]+)\" is not a valid type");
    private static final Pattern NOT_IN_PACKAGE = Pattern.compile("No type (\\w+) found in package ([\\w.]+)");
    private static final Pattern UNRESOLVED = Pattern.compile("Could not resolve symbol for : (\\w+)");
    private static final Pattern NO_FUNCTION = Pattern.compile("No function defined for (\\w+)");
    private static final Pattern ERROR_COUNT = Pattern.compile("gosuc completed with (\\d+) error");
    private static final Pattern ERROR_LINE = Pattern.compile("\\.gsx?:\\[\\d+,\\d+] error:");

    /**
     * What is missing, split for people: {@code types} must be created as stubs; {@code symbols}
     * (constants, functions, typekeys...) are usually members of a missing type or of a type that
     * needs a stub. Names the class declares itself are left out: their errors only follow from
     * a missing type (for example {@code var x = missingCall()}).
     */
    record Missing(List<String> types, List<String> symbols) {
        boolean isEmpty() {
            return types.isEmpty() && symbols.isEmpty();
        }
    }

    private BaselineErrors() { }

    /** The baseline failed because the copied sources do not compile. */
    static boolean isCompilationFailure(String failure) {
        return failure != null && failure.contains("does not compile");
    }

    /** Number of compile errors, or -1 when the text has none. */
    static int errorCount(String failure) {
        if (failure == null) {
            return -1;
        }
        Matcher total = ERROR_COUNT.matcher(failure);
        if (total.find()) {
            return Integer.parseInt(total.group(1));
        }
        int lines = 0;
        Matcher line = ERROR_LINE.matcher(failure);
        while (line.find()) {
            lines++;
        }
        return lines == 0 ? -1 : lines;
    }

    /** Types, packages and members the compiler could not find, sorted and without duplicates. */
    static List<String> unresolvedNames(String failure) {
        return unresolvedNames(failure, List.of());
    }

    /** Like {@link #unresolvedNames(String)}, leaving out names declared in {@code sources}. */
    static List<String> unresolvedNames(String failure, Collection<String> sources) {
        Missing missing = missing(failure, sources);
        Set<String> names = new TreeSet<>();
        Set<String> packages = new TreeSet<>();
        if (failure != null) {
            Matcher inPackage = NOT_IN_PACKAGE.matcher(failure);
            while (inPackage.find()) {
                packages.add(inPackage.group(2) + "." + inPackage.group(1));
            }
        }
        missing.types().forEach(type -> names.add((packages.contains(type) ? "package " : "type or package ") + type));
        missing.symbols().forEach(symbol -> names.add("symbol " + symbol));
        if (failure != null) {
            Matcher function = NO_FUNCTION.matcher(failure);
            while (function.find()) {
                names.add("function " + function.group(1));
            }
        }
        return List.copyOf(names);
    }

    static Missing missing(String failure, Collection<String> sources) {
        Set<String> types = new TreeSet<>();
        Set<String> symbols = new TreeSet<>();
        if (failure == null) {
            return new Missing(List.of(), List.of());
        }
        Matcher type = NOT_A_TYPE.matcher(failure);
        while (type.find()) {
            types.add(type.group(1));
        }
        Matcher inPackage = NOT_IN_PACKAGE.matcher(failure);
        while (inPackage.find()) {
            types.add(inPackage.group(2) + "." + inPackage.group(1));
        }
        Matcher symbol = UNRESOLVED.matcher(failure);
        while (symbol.find()) {
            String name = symbol.group(1);
            if (!declaredIn(name, sources)) {
                symbols.add(name);
            }
        }
        symbols.removeAll(types);
        return new Missing(List.copyOf(types), List.copyOf(symbols));
    }

    /** {@code var name}, {@code name : Type} (parameter or field), {@code \name ->} (block parameter) or a function. */
    static boolean declaredIn(String name, Collection<String> sources) {
        String n = Pattern.quote(name);
        Pattern declaration = Pattern.compile("\\bvar\\s+" + n + "\\b|[(,]\\s*" + n + "\\s*:|\\\\\\s*" + n + "\\b"
                + "|\\bfunction\\s+" + n + "\\b|\\bproperty\\s+get\\s+" + n + "\\b");
        for (String source : sources) {
            if (source != null && declaration.matcher(source).find()) {
                return true;
            }
        }
        return false;
    }
}
