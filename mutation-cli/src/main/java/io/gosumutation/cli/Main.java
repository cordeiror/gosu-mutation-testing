package io.gosumutation.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Entry point of the {@code gosu-mutation} command line tool. */
@Command(name = "gosu-mutation",
        mixinStandardHelpOptions = true,
        versionProvider = Main.Version.class,
        description = "Mutation testing for the Gosu programming language.",
        subcommands = {RunCommand.class, ClassCommand.class, ReportCommand.class, OperatorsCommand.class})
public final class Main implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }

    /** The version written to the jar manifest by the build. */
    static final class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = Main.class.getPackage().getImplementationVersion();
            return new String[] {"gosu-mutation " + (version == null ? "(development build)" : version)};
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
