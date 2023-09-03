"Provides Bazel rule definitions for working with Gradle projects."

load(
    "//internal:task.bzl",
    _gradle_task = "gradle_task",
)

# Exports.
gradle_task = _gradle_task
