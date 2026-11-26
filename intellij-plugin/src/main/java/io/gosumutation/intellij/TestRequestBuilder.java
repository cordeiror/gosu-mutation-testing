package io.gosumutation.intellij;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the request an AI assistant needs to write tests that detect ("kill") the mutants that
 * survived: what mutation testing expects, each undetected mutant as a diff, the class and its tests.
 */
final class TestRequestBuilder {

    /** Characters of test code included; further test files are only listed. */
    static final int MAX_TEST_CHARS = StubRequestBuilder.MAX_TEST_CHARS;

    private TestRequestBuilder() { }

    /**
     * @param undetected survived and uncovered mutants of the class
     * @param tests      the test files of the class (possibly none)
     */
    record Input(Path source, String sourceText, List<MutationResults.Mutant> undetected,
                 List<StubRequestBuilder.SourceFile> tests) {
        Input {
            undetected = List.copyOf(undetected);
            tests = tests == null ? List.of() : List.copyOf(tests);
        }
    }

    static String build(Input in) {
        String fileName = in.source().getFileName().toString();
        StringBuilder md = new StringBuilder();
        md.append("# Write tests that detect the surviving mutants of `").append(fileName).append("`\n\n");
        md.append("Mutation testing changed the class below one small piece at a time (a \"mutant\") and ran its tests. ")
          .append("The tests still pass with each mutant listed here, so a bug of that kind would go unnoticed. ")
          .append("A good test fails when run against the mutated code and passes against the original.\n\n");
        md.append("## Task\n\n");
        md.append("1. For each mutant below, add a test, or strengthen an existing one, that passes on the original class ")
          .append("and would fail with the mutation applied.\n");
        if (in.tests().isEmpty()) {
            md.append("2. The class has no test yet: create a test class `")
              .append(fileName.replaceFirst("\\.gs$", "")).append("Test.gs` in the test folder of the same package.\n");
        } else {
            md.append("2. Put the tests in the existing test class").append(in.tests().size() == 1 ? "" : "es")
              .append(" below and follow their style: base class, setup, naming and assertions.\n");
        }
        md.append("3. Do **not** modify the class under test.\n");
        md.append("4. Assert on results, not on how they are computed. Write `assertEquals(expected, actual)` with the ")
          .append("expected value first.\n");
        md.append("5. Some mutants cannot be detected by any test, because the changed code behaves exactly like the ")
          .append("original for every input (an *equivalent mutant*). Do not write a test for those: explain why the ")
          .append("behavior cannot differ, and tell the user to mark the mutant as equivalent in the Gosu Mutation ")
          .append("tool window (**Mark as Equivalent**). If the explanation shows redundant code, say so.\n");
        md.append("6. When done, list the tests you added or changed, which mutant each one targets, and the mutants ")
          .append("you consider equivalent. Then ask the user to run **Run Mutation Testing** again.\n\n");

        md.append("## Mutants the tests do not detect\n\n");
        int index = 1;
        for (MutationResults.Mutant mutant : in.undetected()) {
            md.append("### ").append(index++).append(". Line ").append(mutant.line());
            if (!mutant.method().isBlank()) {
                md.append(", `").append(mutant.method()).append('`');
            }
            md.append(": ").append(mutant.description()).append("\n\n");
            md.append("- Status: **").append(MutationResults.label(mutant.status())).append("** — ")
              .append(MutationResults.NO_COVERAGE.equals(mutant.status())
                      ? "no test executes this line, so the mutant was not even run."
                      : "every test passed with this change.")
              .append('\n');
            md.append("- Mutant id: `").append(mutant.id()).append("`, operator ").append(mutant.operator()).append("\n\n");
            md.append("```diff\n- ").append(mutant.originalLine()).append("\n+ ").append(mutant.mutatedLine()).append("\n```\n\n");
        }

        md.append("## Class under test: `").append(in.source()).append("`\n\n```gosu\n")
          .append(numbered(in.sourceText())).append("\n```\n\n");

        int budget = MAX_TEST_CHARS;
        List<Path> omitted = new ArrayList<>();
        for (StubRequestBuilder.SourceFile test : in.tests()) {
            String text = test.text().strip();
            if (text.length() > budget) {
                omitted.add(test.path());
                continue;
            }
            budget -= text.length();
            md.append("## Test: `").append(test.path()).append("`\n\n```gosu\n").append(text).append("\n```\n\n");
        }
        if (!omitted.isEmpty()) {
            md.append("## Other test files (not included, to keep this request short; open them if needed)\n\n");
            omitted.forEach(path -> md.append("- `").append(path).append("`\n"));
            md.append('\n');
        }
        return md.toString().stripTrailing() + "\n";
    }

    /** The source with line numbers, so the lines of the mutants are easy to find. */
    private static String numbered(String text) {
        List<String> lines = text.stripTrailing().lines().toList(); // keep leading lines: numbers must match
        int width = String.valueOf(lines.size()).length();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            out.append(String.format("%" + width + "d| ", i + 1)).append(lines.get(i)).append('\n');
        }
        return out.toString().stripTrailing();
    }
}
