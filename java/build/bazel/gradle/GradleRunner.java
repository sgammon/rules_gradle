
package build.bazel.gradle;

import build.bazel.gradle.util.TeeOutputStream;
import com.google.common.base.Joiner;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@CommandLine.Command(
    name = "gradlew",
    version = "bazel-gradle-wrapper 1.0",
    mixinStandardHelpOptions = true,
    showAtFileInUsageHelp = true,
    description = "Wraps an invocation of Gradle from Bazel"
)
public class GradleRunner implements Callable<Integer> {
    static { System.setProperty("logback.configurationFile", "/java/logback.xml"); }

    public static class MappedOutput {
        private final String source;
        private final String target;

        private MappedOutput(String source, String target) {
            this.source = source;
            this.target = target;
        }

        static MappedOutput of(String source, String target) {
            return new MappedOutput(source, target);
        }

        public String getSource() {
            return source;
        }

        public String getTarget() {
            return target;
        }
    }

    static class OutputFileMapConverter implements CommandLine.ITypeConverter<MappedOutput> {
        @Override
        public MappedOutput convert(String subject) {
            if (!subject.contains("=")) {
                throw new IllegalArgumentException("Cannot map --output without target (separated with `=`)");
            }
            String[] portions = subject.split("=");
            if (portions.length != 2) {
                throw new IllegalArgumentException("Too many `=` in `--output`");
            }
            return MappedOutput.of(
                portions[0],
                portions[1]
            );
        }
    }

    private static Logger logging = LoggerFactory.getLogger(GradleRunner.class);

    private static AtomicReference<List<String>> allArgs = new AtomicReference<>();

    @CommandLine.Option(
        names = { "--java_home" },
        required = false,
        description = "Java Home to use for the Gradle build; if not provided, defaults to current JVM."
    )
    private String javaHome = System.getProperty("java.home");

    @CommandLine.Option(
        names = { "--gradle_distribution" },
        required = true,
        description = "Path to a Gradle distribution tarball to use for this build."
    )
    private File gradleDistribution;

    @CommandLine.Option(
        names = { "--gradle_project" },
        required = true,
        description = "Path to the root of the Gradle project."
    )
    private Path gradleProject;

    @CommandLine.Option(
        names = { "--gradle_build_file" },
        required = true,
        description = "Relative path to the specific build file to run; defaults to `build.gradle.kts`."
    )
    private String gradleBuildFile = "build.gradle.kts";

    @CommandLine.Option(
        names = { "--logfile" },
        required = true,
        description = "Path to the log file to write build output to."
    )
    private Path logfilePath;

    @CommandLine.Option(
        names = { "--output" },
        arity = "1..N",
        required = true,
        converter = OutputFileMapConverter.class,
        description = "One or more mapped outputs in the form `source=dest`; each source is copied to `dest`."
    )
    private List<MappedOutput> mappedOutputsList;

    @CommandLine.Parameters(
        paramLabel = "TASK",
        description = "one or more Gradle tasks to run"
    )
    List<String> tasks = new ArrayList<>();

    private static void boot() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    private static File getGradleUserHome(File outDir) {
        return new File(outDir, "_home");
    }

    public static Path getLocalMavenRepo(File outDir) {
        return outDir.toPath().resolve("_tmp_local_maven");
    }

    private static File getRepoDir(File outDir) {
        return new File(outDir, "_repo");
    }

    private static File getInitScript(File outDir) {
        return new File(outDir, "init.script");
    }

    public static void addRepo(File outDir, File repo) throws IOException {
        Gradle.unzip(repo, getRepoDir(outDir));
    }

    private static void putIfNotNull(HashMap<String, String> env, String key, String val) {
        if (val != null) {
            env.put(key, val);
        }
    }

    private static void createInitScript(File initScript, File repoDir) throws IOException {
        String content =
                "allprojects {\n"
                + "  buildscript {\n"
                + "    repositories {\n"
                + "       maven { url '"
                + repoDir.toURI()
                + "'}\n"
                + "    }\n"
                + "  }\n"
                + "  repositories {\n"
                + "    maven {\n"
                + "      url '"
                + repoDir.toURI()
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

    public static void main(String[] args) throws IOException {
        String[] effective = args;
        if (args.length < 1) {
            effective = new String[]{"--help"};
        }

        // static init
        boot();
        GradleRunner.allArgs.set(Arrays.asList(effective));
        int exitCode = new CommandLine(new GradleRunner()).execute(effective);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        final Path root = gradleProject;
        final Path logfile = logfilePath;
        final File distribution = gradleDistribution;
        List<MappedOutput> outputs = mappedOutputsList;
        List<String> taskList = tasks;
        File javaHomeDir = Paths.get(javaHome).toRealPath().toAbsolutePath().toFile();

        ArrayList<String> taskArgs = new ArrayList<>(taskList.size());
        taskArgs.addAll(taskList);

        if (taskArgs.isEmpty()) {
            // run `build` task by default
            taskArgs.add("build");
        }
        logging.debug("Gradle tool root: " + root);
        File base = logfile.toFile().getParentFile();
        File homeDir = getGradleUserHome(base).getAbsoluteFile();
        Path tmpLocalMaven = getLocalMavenRepo(base);

        File repoDir = getRepoDir(base).getAbsoluteFile();
        File initScript = getInitScript(base).getAbsoluteFile();
        createInitScript(initScript, repoDir);

        HashMap<String, String> env = new HashMap<>();
        List<String> arguments = new ArrayList<>();

        putIfNotNull(env, "SystemRoot", System.getenv("SystemRoot"));
        putIfNotNull(env, "TEMP", System.getenv("TEMP"));
        putIfNotNull(env, "TMP", System.getenv("TMP"));

        arguments.add("--offline");
        arguments.add("--init-script");
        arguments.add(initScript.getAbsolutePath());
        arguments.add("-Dmaven.repo.local=" + tmpLocalMaven.toAbsolutePath());
        // Workaround for issue https://github.com/gradle/gradle/issues/5188
        System.setProperty("gradle.user.home", "");

        try {
            if (!Files.exists(root) || !Files.isReadable(root)) {
                logging.error("Could not find gradle tool root or can't read");
                return 1;
            }

            logging.debug(
                "All wrapper arguments: " + Joiner.on(" ").join(GradleRunner.allArgs.get())
            );

            logging.debug(
                "Launching Gradle embedded build (tasks: \"" + String.join("\", \"", taskArgs) + "\")"
            );

            try (ProjectConnection connection = GradleConnector.newConnector()
                    .forProjectDirectory(root.toFile())
                    .useDistribution(distribution.toURI())
                    .useGradleUserHomeDir(homeDir)
                    .connect(); OutputStream log = new BufferedOutputStream(Files.newOutputStream(logfile))) {

                OutputStream out = new TeeOutputStream(System.out, log);
                OutputStream err = new TeeOutputStream(System.err, log);

                connection.newBuild()
                        .forTasks(taskArgs.toArray(new String[0]))
                        .setEnvironmentVariables(env)
                        .setColorOutput(true)
                        .setJavaHome(javaHomeDir)
                        .withArguments(arguments)
                        .setStandardInput(System.in)
                        .setStandardOutput(out)
                        .setStandardError(err)
                        .addProgressListener((ProgressListener) progressEvent ->
                            logging.debug("Gradle status: " + progressEvent.getDescription())
                        )
                        .run();

                GradleTool.mountOutputs(
                    root,
                    logfile,
                    outputs
                );
            }
            return 0;
        } catch (RuntimeException e) {
            logging.error("Could not find Gradle tool root (error)", e);
            // print cwd
            logging.error("Current working directory: " + System.getProperty("user.dir"));
            Files.list(root).forEach(path1 -> logging.error("Path:  " + path1));
            return 1;
        }
    }
}
