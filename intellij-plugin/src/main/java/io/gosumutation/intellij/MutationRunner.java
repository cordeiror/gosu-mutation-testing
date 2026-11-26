package io.gosumutation.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Starts gosu-mutation for one class and reports the outcome. */
final class MutationRunner {

    static final String PLUGIN_ID = "io.github.gosumutation.ide";
    static final String NOTIFICATION_GROUP = "Gosu Mutation Testing";

    private MutationRunner() { }

    static void run(Project project, Path source, List<Path> tests) {
        GosuMutationSettings.State settings = GosuMutationSettings.getInstance().getState();
        TrialPaths paths = TrialPaths.of(source, settings.trialsRoot, settings.stubsDir);
        Path cliLib = cliLib(settings);
        if (cliLib == null || !Files.isDirectory(cliLib)) {
            notify(project, "The gosu-mutation jars were not found" + (cliLib == null ? "." : " in " + cliLib + "."),
                    NotificationType.ERROR);
            return;
        }
        if (paths.stubsDirConfigured() && !Files.isDirectory(paths.stubsDir())) {
            notify(project, "The stubs folder in the settings does not exist: " + paths.stubsDir(), NotificationType.ERROR);
            return;
        }
        Path javaHome = settings.javaHome.isBlank() ? Path.of(System.getProperty("java.home")) : Path.of(settings.javaHome);

        List<String> command = MutationCommand.build(new MutationCommand.Request(javaHome, cliLib, source, tests,
                paths.trialDir(), paths.stubsDirConfigured() ? paths.stubsDir() : null, settings.extraArgs));
        try {
            Files.createDirectories(paths.trialDir());
            Files.deleteIfExists(paths.htmlReport());
            Files.deleteIfExists(paths.baselineFailure());
            Files.deleteIfExists(paths.lastRunLog());
            GeneralCommandLine commandLine = new GeneralCommandLine(command)
                    .withWorkDirectory(paths.trialDir().toFile())
                    .withCharset(StandardCharsets.UTF_8);
            OSProcessHandler handler = new OSProcessHandler(commandLine);
            ProcessTerminatedListener.attach(handler);
            StringBuffer output = new StringBuffer();
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(ProcessEvent event, Key outputType) {
                    output.append(event.getText());
                }
            });
            new RunContentExecutor(project, handler)
                    .withTitle("Mutation: " + source.getFileName() + (tests.isEmpty() ? "" : " (" + TestSelection.describe(tests) + ")"))
                    .withActivateToolWindow(true)
                    .withAfterCompletion(() -> {
                        saveLog(paths, output.toString());
                        ApplicationManager.getApplication().invokeLater(() -> afterRun(project, source, tests, paths));
                    })
                    .run();
        } catch (ExecutionException | IOException e) {
            notify(project, "Could not start gosu-mutation: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    private static void afterRun(Project project, Path source, List<Path> tests, TrialPaths paths) {
        if (project.isDisposed()) {
            return;
        }
        if (Files.isRegularFile(paths.htmlReport())) {
            boolean shown = ShowMutationResultsAction.show(project, source, false);
            MutationResultsService.Loaded loaded = MutationResultsService.getInstance(project).current();
            String summary = shown && loaded != null ? loaded.results().summary() : source.getFileName().toString();
            var notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification("Mutation testing finished: " + source.getFileName(), summary, NotificationType.INFORMATION)
                    .addAction(NotificationAction.createSimpleExpiring("Open HTML report", () -> BrowserUtil.browse(paths.htmlReportUri())));
            if (shown && loaded != null && !loaded.results().undetected().isEmpty()) {
                notification.addAction(NotificationAction.createSimpleExpiring("Build AI test request",
                        () -> TestRequestAction.open(project, loaded)));
            }
            notification.notify(project);
            if (!shown || GosuMutationSettings.getInstance().getState().openHtmlReport) {
                BrowserUtil.browse(paths.htmlReportUri());
            }
            return;
        }
        String failure = read(paths.baselineFailure());
        if (BaselineErrors.isCompilationFailure(failure)) {
            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification("The class or its test does not compile",
                            errorsText(failure) + "It probably uses types that only exist in its original code base. "
                                    + "The stubs wizard explains how to create minimal stand-ins for them.",
                            NotificationType.WARNING)
                    .addAction(NotificationAction.createSimpleExpiring("Open stubs wizard",
                            () -> StubWizardDialog.open(project, source, tests, true)))
                    .addAction(NotificationAction.createSimpleExpiring("Build AI stub request",
                            () -> StubRequest.open(project, source, tests, paths)))
                    .notify(project);
        } else if (failure != null) {
            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification("The tests do not pass on the original class",
                            firstLine(failure) + " If stubs make wrong assumptions, an AI assistant can adjust them.",
                            NotificationType.WARNING)
                    .addAction(NotificationAction.createSimpleExpiring("Build AI stub request",
                            () -> StubRequest.open(project, source, tests, paths)))
                    .notify(project);
        } else {
            notify(project, StubRequestStatus.of(paths, source.getFileName().toString()).message(), NotificationType.WARNING);
        }
    }

    private static String errorsText(String failure) {
        int count = BaselineErrors.errorCount(failure);
        return count > 0 ? count + (count == 1 ? " compile error. " : " compile errors. ") : "";
    }

    private static void saveLog(TrialPaths paths, String output) {
        try {
            Files.createDirectories(paths.lastRunLog().getParent());
            Files.writeString(paths.lastRunLog(), output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // The log only helps to explain a failure; the run itself is not affected.
        }
    }

    /** The bundled jars, or the folder configured in the settings. */
    static Path cliLib(GosuMutationSettings.State settings) {
        if (!settings.cliLib.isBlank()) {
            return Path.of(settings.cliLib);
        }
        var plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        return plugin == null ? null : plugin.getPluginPath().resolve("cli").resolve("lib");
    }

    static void notify(Project project, String content, NotificationType type) {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Gosu mutation testing", content, type)
                .notify(project);
    }

    static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String firstLine(String text) {
        String line = text.strip().lines().findFirst().orElse("");
        return line.length() > 200 ? line.substring(0, 200) + "..." : line;
    }
}
