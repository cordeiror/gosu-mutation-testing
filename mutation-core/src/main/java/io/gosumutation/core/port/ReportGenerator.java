package io.gosumutation.core.port;

import io.gosumutation.core.MutationRunReport;

import java.nio.file.Path;

/** Writes a report in one format. */
public interface ReportGenerator {

    /** Format name used on the command line, e.g. {@code html}. */
    String format();

    /** @return the file that was written inside {@code reportDir} */
    Path generate(MutationRunReport report, Path reportDir);
}
