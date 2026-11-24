package io.gosumutation.runner.worker;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entry point of the child JVM that runs the tests. It discovers every JUnit Platform test below a
 * classes directory, runs them and writes a small line based result file:
 *
 * <pre>
 * TESTS 5
 * FAILED demo.FooTest.bar()
 * </pre>
 *
 * Exit code: 0 all passed, 1 test failures, 2 no tests found. Anything else is a crash.
 */
public final class JUnitWorkerMain {

    public static final int EXIT_PASSED = 0;
    public static final int EXIT_FAILED = 1;
    public static final int EXIT_NO_TESTS = 2;

    private JUnitWorkerMain() { }

    public static void main(String[] args) throws Exception {
        Path classesDir = Path.of(args[0]);
        Path resultFile = Path.of(args[1]);

        AtomicInteger tests = new AtomicInteger();
        List<String> failed = new ArrayList<>();
        TestExecutionListener listener = new TestExecutionListener() {
            @Override
            public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
                boolean failure = result.getStatus() != TestExecutionResult.Status.SUCCESSFUL;
                if (identifier.isTest()) {
                    tests.incrementAndGet();
                }
                // A failing container (e.g. a class whose setup throws) is a failure too.
                if (failure && (identifier.isTest() || identifier.getParentId().isPresent())) {
                    synchronized (failed) {
                        failed.add(nameOf(identifier));
                    }
                    result.getThrowable().ifPresent(throwable ->
                            System.out.println("FAILED " + nameOf(identifier) + ": " + throwable));
                }
            }
        };

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClasspathRoots(Set.of(classesDir)))
                .build();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        List<String> lines = new ArrayList<>();
        lines.add("TESTS " + tests.get());
        failed.stream().sorted().forEach(name -> lines.add("FAILED " + name));
        Files.write(resultFile, lines, StandardCharsets.UTF_8);

        System.out.println("Tests run: " + tests.get() + ", failed: " + failed.size());
        // System.exit also stops the JVM when a test left non daemon threads behind.
        System.exit(!failed.isEmpty() ? EXIT_FAILED : tests.get() == 0 ? EXIT_NO_TESTS : EXIT_PASSED);
    }

    private static String nameOf(TestIdentifier identifier) {
        return identifier.getSource().map(source -> {
            if (source instanceof MethodSource method) {
                return method.getClassName() + "." + method.getMethodName() + "()";
            }
            if (source instanceof ClassSource type) {
                return type.getClassName();
            }
            return identifier.getDisplayName();
        }).orElse(identifier.getDisplayName());
    }
}
