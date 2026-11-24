package io.gosumutation.runner;

import io.gosumutation.core.port.TestRunner;
import io.gosumutation.core.process.ForkedJvm;
import io.gosumutation.core.process.ProcessIsolation;
import io.gosumutation.runner.worker.JUnitWorkerMain;
import org.jacoco.agent.AgentJar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the tests of a compiled standalone Gosu project. Gosu classes compile to ordinary JVM class
 * files, so tests written in Gosu with JUnit 5 (or JUnit 4) annotations are executed by the JUnit
 * Platform in a child JVM ({@link JUnitWorkerMain}).
 */
public final class GosuTestRunner implements TestRunner {

    private static volatile Path jacocoAgent;

    @Override
    public TestExecutionResult execute(TestExecutionRequest request) {
        try {
            Files.createDirectories(request.workDir());
            Path resultFile = Files.createTempFile(request.workDir(), "tests-", ".result");

            List<Path> classpath = new ArrayList<>();
            classpath.add(request.classesDir());
            classpath.addAll(request.classpath());
            classpath.addAll(ForkedJvm.frameworkClasspath());

            List<String> jvmArgs = new ArrayList<>();
            if (request.coverageFile() != null) {
                jvmArgs.add("-javaagent:" + jacocoAgent() + "=destfile=" + request.coverageFile() + ",output=file,dumponexit=true");
            }

            List<String> command = ForkedJvm.command(request.workDir(), jvmArgs, classpath, JUnitWorkerMain.class.getName(),
                    List.of(request.classesDir().toString(), resultFile.toString()));
            ProcessIsolation.ProcessResult process = ProcessIsolation.run(command, request.workDir(), request.timeout());

            if (process.timedOut()) {
                return new TestExecutionResult(TestStatus.TIMED_OUT, 0, List.of(), process.output(), process.durationMillis());
            }
            int testsRun = 0;
            List<String> failed = new ArrayList<>();
            for (String line : Files.readAllLines(resultFile, StandardCharsets.UTF_8)) {
                if (line.startsWith("TESTS ")) {
                    testsRun = Integer.parseInt(line.substring(6).trim());
                } else if (line.startsWith("FAILED ")) {
                    failed.add(line.substring(7));
                }
            }
            TestStatus status = switch (process.exitCode()) {
                case JUnitWorkerMain.EXIT_PASSED -> TestStatus.PASSED;
                case JUnitWorkerMain.EXIT_FAILED -> failed.isEmpty() ? TestStatus.ERROR : TestStatus.FAILED;
                default -> TestStatus.ERROR;
            };
            String output = process.exitCode() == JUnitWorkerMain.EXIT_NO_TESTS
                    ? "No tests were found in " + request.classesDir() + "\n" + process.output()
                    : process.output();
            return new TestExecutionResult(status, testsRun, failed, output, process.durationMillis());
        } catch (IOException | RuntimeException e) {
            return new TestExecutionResult(TestStatus.ERROR, 0, List.of(), e.toString(), 0);
        }
    }

    private static Path jacocoAgent() throws IOException {
        Path agent = jacocoAgent;
        if (agent == null) {
            synchronized (GosuTestRunner.class) {
                agent = jacocoAgent;
                if (agent == null) {
                    agent = AgentJar.extractToTempLocation().toPath();
                    jacocoAgent = agent;
                }
            }
        }
        return agent;
    }
}
