package build.bazel.gradle;

import com.google.common.base.MoreObjects;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A wrapper to easily call gradle from Java. To use it:
 *
 * <pre>
 *     // Create the wrapper with the distribution to use the project and where to write stuff.
 *     Gradle gradle = new Gradle(project_dir, scratch_dir, gradle_distribution);
 *     // Add local maven repo zips that will be available to the build.
 *     gradle.addRepo(path_to_zip);
 *     // Run a task
 *     gradle.run("assembleDebug");
 *     // Clean the scratch dir and close the daemon
 *     gradle.close();
 * </pre>
 */
public class Gradle implements Closeable {
    private static Logger logging = LoggerFactory.getLogger(GradleRunner.class);
    private final File outDir;
    private final File distribution;
    private final File project;
    private final List<String> arguments;

    public Gradle(File project, File outDir, File distribution)
            throws IOException {
        this.project = project;
        this.outDir = outDir;
        this.distribution = distribution;
        this.arguments = new LinkedList<>();
        File initScript = getInitScript().getAbsoluteFile();
        File repoDir = getRepoDir().getAbsoluteFile();
        FileUtils.cleanOutputDir(outDir);
        Files.createDirectories(getBuildDir().toPath());
        createInitScript(initScript, repoDir);
    }

    public void addRepo(File repo) throws IOException {
        unzip(repo, getRepoDir());
    }

    public void addArgument(String argument) {
        arguments.add(argument);
    }

    public void withProfiler() {
        arguments.add("--profile");
    }

    private File getInitScript() {
        return new File(outDir, "init.script");
    }

    public void run(String task) {
        run(Collections.singletonList(task), System.out, System.err);
    }

    public void run(List<String> tasks) {
        run(tasks, System.out, System.err);
    }

    public void run(List<String> tasks, OutputStream out, OutputStream err) {
        File buildDir = getBuildDir().getAbsoluteFile();
        File homeDir = getGradleUserHome().getAbsoluteFile();
        // gradle tries to write into .m2 so we pass it a tmp one.
        Path tmpLocalMaven = getLocalMavenRepo();
        HashMap<String, String> env = new HashMap<>();
        env.put("BUILD_DIR", buildDir.getAbsolutePath());
        // On windows it is needed to set a few more environment variables
        // Variable should be set only if not null to avoid exceptions in Gradle such as
        // "java.lang.IllegalArgumentException: Cannot encode a null string."
        putIfNotNull(env, "SystemRoot", System.getenv("SystemRoot"));
        putIfNotNull(env, "TEMP", System.getenv("TEMP"));
        putIfNotNull(env, "TMP", System.getenv("TMP"));
        List<String> arguments = new ArrayList<>();
        arguments.add("--offline");
        arguments.add("--info");
        arguments.add("--init-script");
        arguments.add(getInitScript().getAbsolutePath());
        arguments.add("-Dmaven.repo.local=" + tmpLocalMaven.toAbsolutePath().toString());
        // Workaround for issue https://github.com/gradle/gradle/issues/5188
        System.setProperty("gradle.user.home", "");
        arguments.addAll(this.arguments);

        ProjectConnection projectConnection = getProjectConnection(homeDir, project, distribution);
        try {
            BuildLauncher launcher =
                    projectConnection
                            .newBuild()
                            .setEnvironmentVariables(env)
                            .withArguments(arguments)
                            .setColorOutput(true)
                            .setStandardInput(System.in)
                            .setStandardOutput(out)
                            .setStandardError(err)
                            .addProgressListener((ProgressListener) progressEvent ->
                                    logging.info("Gradle status: " + progressEvent.getDescription())
                            )
                            .forTasks(tasks.toArray(new String[0]));
            launcher.run();
        } catch (Exception e) {
            throw new RuntimeException("Gradle invocation failed: " + this, e);
        } finally {
            projectConnection.close();
        }
    }

    private static void putIfNotNull(HashMap<String, String> env, String key, String val) {
        if (val != null) {
            env.put(key, val);
        }
    }

    @Override
    public void close() throws IOException {
        // Shut down the daemon so it doesn't hold the lock on any of the files.
        // Note that this is internal Gradle API, but is used by Studio and Intellij so is
        // relatively stable.
        // Because this circumvents the connector we must set gradle.user.home for it to work
        System.setProperty("gradle.user.home", getGradleUserHome().getAbsolutePath());
        DefaultGradleConnector.close();
        maybeCopyProfiles();
        try {
            FileUtils.cleanOutputDir(outDir);
        } catch (Exception e) {
            // Allow this to fail, as it will be cleaned up by the next run (b/77804450)
            // This is an issue on windows without the sandbox and with stricter file locking
            System.err.println(
                    "Failed to cleanup output directory. Will be cleaned up at next invocation");
        }
    }

    private void maybeCopyProfiles() throws IOException {
        Path profiles = project.toPath().resolve("build").resolve("reports").resolve("profile");
        if (!Files.isDirectory(profiles)) {
            return;
        }
        Path destination = TestUtils.getTestOutputDir().resolve("gradle_profiles");
        copyDirectory(profiles, destination);
    }

    private static void copyDirectory(Path from, Path to) throws IOException {
        Files.walkFileTree(
                from,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        Files.createDirectory(to.resolve(from.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.copy(file, to.resolve(from.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private File getGradleUserHome() {
        return new File(outDir, "_home");
    }

    public File getBuildDir() {
        return outDir;
    }

    public Path getLocalMavenRepo() {
        return outDir.toPath().resolve("_tmp_local_maven");
    }

    private File getRepoDir() {
        return new File(outDir, "_repo");
    }

    private static void createInitScript(File initScript, File repoDir) throws IOException {
        String content =
                "allprojects {\n"
                        + "  buildscript {\n"
                        + "    repositories {\n"
                        + "       maven { url '"
                        + repoDir.toURI().toString()
                        + "'}\n"
                        + "    }\n"
                        + "  }\n"
                        + "  repositories {\n"
                        + "    maven {\n"
                        + "      url '"
                        + repoDir.toURI().toString()
                        + "'\n"
                        + "      metadataSources {\n"
                        + "        mavenPom()\n"
                        + "        artifact()\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "rootProject {\n"
                        + "    task cleanLocalCaches(type: Delete) {\n"
                        + "       delete fileTree(gradle.gradleUserHomeDir.toString() + '/caches/transforms-2') { "
                        + "           exclude '*.lock'\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n";
        try (FileWriter writer = new FileWriter(initScript)) {
            writer.write(content);
        }
    }

    public static void unzip(File zip, File out) throws IOException {
        byte[] buffer = new byte[1024];
        try (FileInputStream fis = new FileInputStream(zip);
                BufferedInputStream bis = new BufferedInputStream(fis);
                ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                File newFile = new File(out, fileName);
                if (!fileName.endsWith("/")) {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }

    private static ProjectConnection getProjectConnection(
            File home, File projectDirectory, File distribution) {
        return GradleConnector.newConnector()
                .useDistribution(distribution.toURI())
                .useGradleUserHomeDir(home)
                .forProjectDirectory(projectDirectory)
                .connect();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("outDir", outDir)
                .add("distribution", distribution)
                .add("project", project)
                .add("arguments", arguments)
                .toString();
    }
}
