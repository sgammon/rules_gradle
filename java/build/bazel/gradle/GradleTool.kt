
package build.bazel.gradle

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists


object GradleTool {
    // Logger.
    private val logging: Logger = LoggerFactory.getLogger(GradleTool::class.java.name)

    @JvmStatic private fun outputTarget(project: Path, relative: String, target: String): Pair<Path, File> {
        // base:     `bazel-out/darwin_arm64-fastbuild/bin/e2e/project`
        // project:  `e2e/project`
        // relative: `build/libs/sample-project.jar`
        // target:   `bazel-out/darwin_arm64-fastbuild/bin/e2e/project/build/libs/sample-project.jar`
        return (
            // source:
            // `e2e/project/build/libs/sample-project.jar`
            project.resolve(relative)
        ) to (
            // target:
            Path.of(target).toFile()
        )
    }

    @JvmStatic fun mountOutputs(root: Path, logFilePath: Path, outputs: List<GradleRunner.MappedOutput>) {
        val cwd = Path(System.getProperty("user.dir"))
        val logFile = root.resolve(logFilePath)
        val outBase = logFile.parent

        if (!outBase.exists()) {
            Files.createDirectories(outBase)
        }
        if (!outBase.exists()) error(
            "Cannot locate output base for Gradle artifacts"
        )

        outputs.map {
            val artifact = it.source
            val relativeTarget = it.target
            val (source, target) = outputTarget(root, artifact, relativeTarget)
            logging.info("Checking output ${cwd}/$artifact (target: $target)")

            cwd.resolve(source).toFile().let { file ->
                if (!file.exists()) {
                    error("Output does not exist: ${file.path}")
                }
                val outDir = target.parentFile
                if (!outDir.exists()) {
                    Files.createDirectories(outDir.toPath())
                }

                // copy the output
                file.copyTo(
                    target = target,
                    overwrite = false,
                )
                logging.info("Output copied to Bazel base.")
            }
        }
    }
}
