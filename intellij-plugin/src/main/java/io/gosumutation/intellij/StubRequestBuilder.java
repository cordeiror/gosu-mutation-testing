package io.gosumutation.intellij;

import java.nio.file.Path;
import java.util.List;

/**
 * Builds the request an AI assistant needs to create the stand-in types ("stubs") of a class:
 * what to do, where, the compiler errors, the class, its test and the stubbing guide.
 */
final class StubRequestBuilder {

    static final int MAX_FAILURE_LINES = 300;

    private StubRequestBuilder() { }

    /**
     * @param test        the test class, or null when it is not known
     * @param testText    its content, or null
     * @param failure     baseline failure text written by gosu-mutation
     * @param stubsDir    folder where the stubs must be written
     * @param existing    stub files already present, relative to {@code stubsDir}
     * @param guide       content of docs/stubbing-guide.md
     */
    record Input(Path source, String sourceText, Path test, String testText, String failure, Path stubsDir,
                 List<String> existing, String guide) { }

    static String build(Input in) {
        StringBuilder md = new StringBuilder();
        md.append("# Create Gosu stubs so a class can be mutation tested\n\n");
        md.append("The class below does not compile on its own because it uses types from its original code base. ")
          .append("Create minimal stand-in types (\"stubs\") so that the class and its test compile and the test passes, ")
          .append("following the stubbing guide at the end of this request.\n\n");
        md.append("## Task\n\n");
        md.append("1. Write the stubs as Gosu `.gs` files under this folder, one file per type, in folders matching the package:\n")
          .append("   `").append(in.stubsDir()).append("`\n");
        md.append("2. Do **not** modify the class under test");
        if (in.test() != null) {
            md.append(" or its test");
        }
        md.append(". Do not copy their implementation into the stubs.\n");
        md.append("3. Create only the types and members that the class and the test use.\n");
        md.append("4. Start each stub's doc comment with `STUB:` and state the assumptions it makes.\n");
        md.append("5. When done, list the stubs you created and every assumption, so the user can check them, ")
          .append("then ask the user to run **Run Mutation Testing** again. If new errors appear, they will build a new request.\n\n");

        List<String> unresolved = BaselineErrors.unresolvedNames(in.failure());
        if (!unresolved.isEmpty()) {
            md.append("## Names the compiler could not resolve\n\n");
            unresolved.forEach(name -> md.append("- ").append(name).append('\n'));
            md.append('\n');
        }

        md.append("## Compiler output\n\n```text\n").append(limit(in.failure())).append("\n```\n\n");

        md.append("## Class under test: `").append(in.source()).append("`\n\n```gosu\n")
          .append(in.sourceText().strip()).append("\n```\n\n");
        if (in.test() != null && in.testText() != null) {
            md.append("## Test: `").append(in.test()).append("`\n\n```gosu\n")
              .append(in.testText().strip()).append("\n```\n\n");
        }

        md.append("## Stubs that already exist\n\n");
        if (in.existing().isEmpty()) {
            md.append("None yet.\n\n");
        } else {
            in.existing().forEach(file -> md.append("- `").append(file).append("`\n"));
            md.append("\nExtend them instead of creating duplicates.\n\n");
        }

        md.append("---\n\n").append(in.guide().strip()).append('\n');
        return md.toString();
    }

    private static String limit(String failure) {
        if (failure == null) {
            return "";
        }
        String[] lines = failure.strip().split("\\R");
        if (lines.length <= MAX_FAILURE_LINES) {
            return String.join("\n", lines);
        }
        return String.join("\n", java.util.Arrays.copyOf(lines, MAX_FAILURE_LINES))
                + "\n... (" + (lines.length - MAX_FAILURE_LINES) + " more lines)";
    }
}
