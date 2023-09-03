
package build.bazel.gradle;

import build.bazel.gradle.util.TeeOutputStream;
import com.google.common.base.Joiner;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class GradleRunner {
    private static Logger logging = LoggerFactory.getLogger(GradleRunner.class);

    private static File getGradleUserHome(File outDir) {
        return new File(outDir, "_home");
    }

    public static void main(String args[]) throws IOException {
        if (args.length < 5) {
            System.out.println("usage: java -jar gradlerunner.jar <gradle-root> <build-file> <output-log> <distribution> [--output=file/path] <tasks>");
            System.exit(1);
            return;
        }
        // project_root
        // ctx.file.build_file.path
        // ctx.outputs.output_log.path
        // ctx.attr.distribution
        // --output=<outputs>
        // <tasks>
        List<String> allArgs = Arrays.asList(args);
        final String root = allArgs.get(0);
        final String buildfile = allArgs.get(1);
        final String logfile = allArgs.get(2);
        final File distribution = new File(allArgs.get(3));
        List<String[]> outputs = allArgs.stream().filter((i) -> i.startsWith("--output=")).map(
            (i) -> i.replace("--output=", "")
        ).map(
            (i) -> i.split("=")
        ).collect(
            Collectors.toList()
        );
        Path logFile = Paths.get(logfile);
        List<String> taskList = allArgs.subList(4 + outputs.size(), allArgs.size() - 1);
        ArrayList<String> taskArgs = new ArrayList<>(taskList.size());
        taskArgs.addAll(taskList);

        if (taskArgs.isEmpty()) {
            // run `build` task by default
            taskArgs.add("build");
        }
        logging.debug("Gradle tool root: " + root);
        Path path = Path.of(root);
        File homeDir = getGradleUserHome(logFile.toFile().getParentFile()).getAbsoluteFile();

        try {
            if (!Files.exists(path) || !Files.isReadable(path)) {
                logging.error("could not find gradle tool root or can't read");
                System.exit(1);
                return;
            }

            logging.info(
                "ALL ARGS: " + Joiner.on(" ").join(allArgs)
            );

            logging.info(
                "Launching Gradle embedded build (tasks: \"" + String.join("\", \"", taskArgs) + "\")"
            );

            try (ProjectConnection connection = GradleConnector.newConnector()
                    .forProjectDirectory(path.toFile())
                    .useDistribution(distribution.toURI())
                    .useGradleUserHomeDir(homeDir)
                    .connect(); OutputStream log = new BufferedOutputStream(Files.newOutputStream(logFile))) {

                OutputStream out = new TeeOutputStream(System.out, log);
                OutputStream err = new TeeOutputStream(System.err, log);

                connection.newBuild()
                        .forTasks(taskArgs.toArray(new String[0]))
                        .setColorOutput(true)
                        .addArguments("--info")
                        .setStandardInput(System.in)
                        .setStandardOutput(out)
                        .setStandardError(err)
                        .addProgressListener((ProgressListener) progressEvent ->
                                logging.info("Gradle status: " + progressEvent.getDescription())
                        ).run();

                GradleTool.mountOutputs(
                    root,
                    logfile,
                    outputs
                );
            }

            System.exit(0);
        } catch (RuntimeException e) {
            logging.error("could not find gradle tool root (error)", e);
            // print cwd
            logging.error("Current working directory: " + System.getProperty("user.dir"));
            Files.list(path).forEach(path1 -> logging.error("Path:  " + path1));
            System.exit(1);
        }
    }
}
