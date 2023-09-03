
package build.bazel.gradle;

import build.bazel.gradle.util.TeeOutputStream;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


class GradleW {
    public static void main(String[] args) throws IOException {
        System.exit(new GradleW().run(Arrays.asList(args)));
    }
    private int run(List<String> args) throws IOException {
        if (args.isEmpty()) {
            System.out.println("usage: java -jar gradlew.jar (options)");
            return 1;
        }
        Path logFile = null;
        File gradleFile = null;
        File distribution = null;
        LinkedList<File> repos = new LinkedList<>();
        LinkedList<String> tasks = new LinkedList<>();
        LinkedList<OutputFileEntry> outputFiles = new LinkedList<>();
        List<String> gradleArgs = new ArrayList<>();
        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("--output") && it.hasNext()) {
                String from = it.next();
                if (!it.hasNext()) {
                    throw new IllegalArgumentException("--output requires two arguments.");
                }
                String to = it.next();
                outputFiles.add(new OutputFileEntry(from, to));
            } else if (arg.equals("--gradle_file") && it.hasNext()) {
                gradleFile = new File(it.next());
            } else if (arg.equals("--distribution") && it.hasNext()) {
                distribution = new File(it.next());
            } else if (arg.equals("--repo") && it.hasNext()) {
                repos.add(new File(it.next()));
            } else if (arg.equals("--task") && it.hasNext()) {
                tasks.add(it.next());
            } else if (arg.equals("--log_file") && it.hasNext()) {
                logFile = Paths.get(it.next());
            } else if (arg.equals("--max_workers") && it.hasNext()) {
                gradleArgs.add("--max-workers");
                gradleArgs.add(it.next());
            } else {
                throw new IllegalArgumentException("Unknown argument '" + arg + "'.");
            }
        }

        File outDir =
                gradleFile.getParentFile().toPath().resolve("build").toFile();
//        Files.createDirectories(outDir.toPath());

        try (Gradle gradle = new Gradle(gradleFile.getParentFile(), outDir, distribution);
                OutputStream log = new BufferedOutputStream(Files.newOutputStream(logFile))) {
            for (File repo : repos) {
                gradle.addRepo(repo);
            }
            gradleArgs.forEach(gradle::addArgument);
            OutputStream out = new TeeOutputStream(System.out, log);
            OutputStream err = new TeeOutputStream(System.err, log);
            gradle.run(tasks, out, err);
            for (OutputFileEntry outputFile : outputFiles) {
                outputFile.collect(gradle.getBuildDir().toPath(), gradle.getLocalMavenRepo());
            }
        }
        return 0;
    }
    private static class OutputFileEntry {
        private enum Type {
            OUTPUT_FILE("build/"),
            LOCAL_MAVEN_FILE("m2/");
            final String prefix;
            Type(String prefix) {
                this.prefix = prefix;
            }
        }
        final String from;
        final Type type;
        final Path to;
        OutputFileEntry(String from, String to) {
            this.to = Paths.get(to);
            for (Type value : Type.values()) {
                if (from.contains(value.prefix)) {
                    this.type = value;
                    this.from = from;
                    return;
                }
            }
            throw new RuntimeException("Unknown output entry type for " + from);
        }

        void collect(Path outputDir, Path m2Repository) throws IOException {
            Path searchDir = getSearchDir(outputDir, m2Repository);
            PathMatcher pathMatcher = searchDir.getFileSystem().getPathMatcher("glob:" + from);
            PathFinder pathFinder = new PathFinder(searchDir, pathMatcher);
            Files.walkFileTree(searchDir, pathFinder);
            Preconditions.checkNotNull(
                    pathFinder.result, "Output file %s not found in %s", from, searchDir);
            Files.copy(pathFinder.result, to);
        }

        private class PathFinder extends SimpleFileVisitor<Path> {
            private final Path searchDir;
            private final PathMatcher search;
            Path result = null;
            private PathFinder(Path searchDir, PathMatcher search) {
                this.searchDir = searchDir;
                this.search = search;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (search.matches(searchDir.relativize(file))) {
                    if (result != null) {
                        throw new IOException(
                                "Multiple matches for "
                                        + from
                                        + " in dir "
                                        + searchDir
                                        + "\n 1. "
                                        + result
                                        + "\n 2. "
                                        + file);
                    }
                    result = file;
                }
                return FileVisitResult.CONTINUE;
            }
        }
        private Path getSearchDir(Path outputDir, Path m2Repository) {
            switch (type) {
                case OUTPUT_FILE:
                    return outputDir;
                case LOCAL_MAVEN_FILE:
                    return m2Repository;
            }
            throw new IllegalStateException("Unknown output entry type " + type);
        }
    }
}
