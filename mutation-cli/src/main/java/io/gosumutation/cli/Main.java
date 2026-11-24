package io.gosumutation.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Entry point of the {@code gosu-mutation} command line tool. */
@Command(name = "gosu-mutation",
        mixinStandardHelpOptions = true,
        version = "gosu-mutation 0.1.0",
        description = "Mutation testing for the Gosu programming language.",
        subcommands = {RunCommand.class, ClassCommand.class, ReportCommand.class, OperatorsCommand.class})
public final class Main implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
