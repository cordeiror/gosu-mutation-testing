package io.gosumutation.intellij;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Lets the user pick the tests of a class: any mix of test files and folders, searched recursively. */
final class TestChooser {

    private TestChooser() { }

    /** @return the chosen files and folders, or null when cancelled */
    static List<Path> choose(Project project, VirtualFile sourceFile, Path startAt) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.multiFilesOrDirs()
                .withTitle("Choose the Tests of " + sourceFile.getName())
                .withDescription("Select test files and/or folders (Ctrl/Cmd+click for several). "
                        + "Folders are searched recursively for Gosu files.")
                .withFileFilter(file -> file.isDirectory() || "gs".equals(file.getExtension()) || "gsx".equals(file.getExtension()));
        VirtualFile start = startAt == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByNioFile(startAt);
        VirtualFile[] chosen = FileChooser.chooseFiles(descriptor, project, start != null ? start : sourceFile.getParent());
        if (chosen.length == 0) {
            return null;
        }
        return Arrays.stream(chosen).map(GosuFiles::pathOf).toList();
    }
}
