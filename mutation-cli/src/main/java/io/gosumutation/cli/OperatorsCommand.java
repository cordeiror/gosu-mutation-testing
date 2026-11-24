package io.gosumutation.cli;

import io.gosumutation.gosu.GosuMutationOperator;
import io.gosumutation.gosu.GosuOperators;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Command(name = "operators", mixinStandardHelpOptions = true, description = "Lists the available mutation operators.")
final class OperatorsCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        for (GosuMutationOperator operator : GosuOperators.available()) {
            out.printf("%-20s %-15s %s%n", operator.id(), operator.category(), operator.description());
        }
        out.flush();
        return ExitCodes.OK;
    }
}
