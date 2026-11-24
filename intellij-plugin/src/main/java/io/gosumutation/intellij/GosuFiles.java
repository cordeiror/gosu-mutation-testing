package io.gosumutation.intellij;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;

/** Helpers to find the Gosu class an action applies to. */
final class GosuFiles {

    private GosuFiles() { }

    /** The selected local {@code .gs} file, or null. */
    static VirtualFile selectedClass(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null || file.isDirectory() || !"gs".equals(file.getExtension()) || !file.isInLocalFileSystem()) {
            return null;
        }
        return file;
    }

    static Path pathOf(VirtualFile file) {
        return Path.of(file.getPath());
    }
}
