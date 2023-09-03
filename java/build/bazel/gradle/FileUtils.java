
package build.bazel.gradle;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;


public final class FileUtils {
    private static final Joiner PATH_JOINER = Joiner.on(File.separatorChar);
    private static final Joiner COMMA_SEPARATED_JOINER = Joiner.on(", ");
    private static final Joiner UNIX_NEW_LINE_JOINER = Joiner.on('\n');

    public FileUtils() { }

    /**
     * Deletes a file or a directory if it exists. If the directory is not empty, its contents will
     * be deleted recursively.
     *
     * @param file the file or directory to delete. The file/directory may not exist; if the
     *     directory exists, it may be non-empty.
     */
    public static void deleteRecursivelyIfExists(File file) throws IOException {
        PathUtils.deleteRecursivelyIfExists(file.toPath());
    }

    /**
     * Recursively deletes a path.
     *
     * @param path the path delete, may exist or not
     * @throws IOException failed to delete the file / directory
     */
    public static void deletePath(final File path) throws IOException {
        deleteRecursivelyIfExists(path);
    }

    /**
     * Recursively deletes a directory content (including the sub directories) but not itself.
     *
     * @param directory the directory, that must exist and be a valid directory
     * @throws IOException failed to delete the file / directory
     */
    public static void deleteDirectoryContents(final File directory) throws IOException {
        Preconditions.checkArgument(directory.isDirectory(), "!directory.isDirectory");

        File[] files = directory.listFiles();
        Preconditions.checkNotNull(files);
        for (File file : files) {
            deletePath(file);
        }
    }

    /**
     * Makes sure {@code path} is an empty directory. If {@code path} is a directory, its contents
     * are removed recursively, leaving an empty directory. If {@code path} is not a directory,
     * it is removed and a directory created with the given path. If {@code path} does not
     * exist, a directory is created with the given path.
     *
     * @param path the path, that may exist or not and may be a file or directory
     * @throws IOException failed to delete directory contents, failed to delete {@code path} or
     * failed to create a directory at {@code path}
     */
    public static void cleanOutputDir(File path) throws IOException {
        if (!path.isDirectory()) {
            if (path.exists()) {
                deletePath(path);
            }

            if (!path.mkdirs()) {
                throw new IOException(String.format("Could not create empty folder %s", path));
            }

            return;
        }

        deleteDirectoryContents(path);
    }
}
