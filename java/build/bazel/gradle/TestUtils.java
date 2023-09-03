
package build.bazel.gradle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;


public class TestUtils {
    /**
     * Creates a temporary directory that is deleted when the JVM exits.
     *
     * @deprecated Temporary directories and files should be deleted after each test, not kept
     *     around for the lifetime of the JVM. This can be achieved by using
     *     a `org.junit.rules.TemporaryFolder` rule, or by calling the
     *     {@link PathUtils#deleteRecursivelyIfExists(Path)} method in {@code tearDown}.
     */
    @Deprecated
    public static Path createTempDirDeletedOnExit() throws IOException {
        Path tempDir = Files.createTempDirectory("");
        PathUtils.addRemovePathHook(tempDir);
        return tempDir;
    }

    /**
     * Returns a directory which tests can output data to. If running through Bazel's test runner,
     * this returns the directory as specified by the TEST_UNDECLARED_OUTPUTS_DIR environment
     * variable. Data written to this directory will be zipped and made available under the
     * WORKSPACE_ROOT/bazel-testlogs/ after the test completes. For non-Bazel runs, this currently
     * returns a tmp directory that is deleted on exit.
     */
    public static Path getTestOutputDir() throws IOException {
        // If running via bazel, returns the sandboxed test output dir.
        String testOutputDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
        if (testOutputDir != null) {
            return Paths.get(testOutputDir);
        }

        return createTempDirDeletedOnExit();
    }
}
