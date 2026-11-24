package io.gosumutation.intellij;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;

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

    static void run(Project project, Path source, Path test) {
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

        List<String> command = MutationCommand.build(new MutationCommand.Request(javaHome, cliLib, source, test,
                paths.trialDir(), paths.stubsDirConfigured() ? paths.stubsDir() : null, settings.extraArgs));
        try {
            Files.createDirectories(paths.trialDir());
            Files.deleteIfExists(paths.htmlReport());
            Files.deleteIfExists(paths.baselineFailure());
            GeneralCommandLine commandLine = new GeneralCommandLine(command)
                    .withWorkDirectory(paths.trialDir().toFile())
                    .withCharset(StandardCharsets.UTF_8);
            OSProcessHandler handler = new OSProcessHandler(commandLine);
            ProcessTerminatedListener.attach(handler);
            new RunContentExecutor(project, handler)
                    .withTitle("Mutation: " + source.getFileName())
                    .withActivateToolWindow(true)
                    .withAfterCompletion(() -> ApplicationManager.getApplication().invokeLater(
                            () -> afterRun(project, source, test, paths)))
                    .run();
        } catch (ExecutionException | IOException e) {
            notify(project, "Could not start gosu-mutation: " + e.getMessage(), NotificationType.ERROR);
        }
    }

    private static void afterRun(Project project, Path source, Path test, TrialPaths paths) {
        if (project.isDisposed()) {
            return;
        }
        if (Files.isRegularFile(paths.htmlReport())) {
            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification("Mutation testing finished", source.getFileName().toString(), NotificationType.INFORMATION)
                    .addAction(NotificationAction.createSimpleExpiring("Open report", () -> BrowserUtil.browse(paths.htmlReportUri())))
                    .notify(project);
            BrowserUtil.browse(paths.htmlReportUri());
            return;
        }
        String failure = read(paths.baselineFailure());
        if (BaselineErrors.isCompilationFailure(failure)) {
            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification("The class or its test does not compile",
                            "It probably uses types that only exist in its original code base. "
                                    + "An AI assistant can create minimal stand-ins for them.",
                            NotificationType.WARNING)
                    .addAction(NotificationAction.createSimpleExpiring("Build AI stub request",
                            () -> StubRequest.open(project, source, test, paths)))
                    .notify(project);
        } else if (failure != null) {
            notify(project, "The baseline failed; see the Run window. " + firstLine(failure), NotificationType.WARNING);
        } else {
            notify(project, "gosu-mutation did not produce a report; see the Run window.", NotificationType.WARNING);
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
