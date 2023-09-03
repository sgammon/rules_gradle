"""Defines Bazel rules for interacting with Gradle."""

GRADLE_WRAPPER = "@rules_gradle//gradle/wrapper"
GRADLE_TOOL_RUNNER = "@rules_gradle//java:wrapper"
JAVA_TOOLCHAIN_TYPE = "@bazel_tools//tools/jdk:runtime_toolchain_type"

def _gradle_build_impl(ctx):
    args = []
    outputs = [ctx.outputs.output_log]
    transitive_inputs = []

    # resolve java toolchain
    transitive_inputs.append(ctx.toolchains[JAVA_TOOLCHAIN_TYPE].java_runtime.files)

    args += ["--log_file", ctx.outputs.output_log.path]
    args += ["--gradle_file", ctx.file.build_file.path]

    if (ctx.attr.output_file_destinations):
        for source, dest in zip(ctx.attr.output_file_sources, ctx.outputs.output_file_destinations):
            outputs += [dest]
            args += ["--output", source, dest.path]

    distribution = ctx.attr.distribution.files.to_list()[0]
    args += ["--distribution", distribution.path]
    for repo in ctx.files.repos:
        args += ["--repo", repo.path]
    for task in ctx.attr.tasks:
        args += ["--task", task]
    if ctx.attr.max_workers > 0:
        args += ["--max_workers", str(ctx.attr.max_workers)]

    ctx.actions.run(
        inputs = depset(
            ctx.files.data + ctx.files.repos + [ctx.file.build_file, distribution],
            transitive = transitive_inputs,
        ),
        outputs = outputs,
        mnemonic = "gradlew",
        arguments = args,
        executable = ctx.executable._gradlew,
        toolchain = JAVA_TOOLCHAIN_TYPE,
        tools = [
            ctx.executable._gradlew,
        ],
    )

# This rule is wrapped to allow the output Label to location map to be expressed as a map in the
# build files.
_gradle_build_rule = rule(
    attrs = {
        "data": attr.label_list(allow_files = True),
        "output_file_sources": attr.string_list(),
        "output_file_destinations": attr.output_list(),
        "tasks": attr.string_list(),
        "build_file": attr.label(
            allow_single_file = True,
        ),
        "repos": attr.label_list(allow_files = True),
        "output_log": attr.output(),
        "distribution": attr.label(allow_files = True),
        "max_workers": attr.int(default = 0, doc = "Max number of workers, 0 or negative means unset (Gradle will use the default: number of CPU cores)."),
        "_gradlew": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//java:wrapper"),
            allow_files = True,
        ),
    },
    fragments = [
        "java",
    ],
    toolchains = [
        JAVA_TOOLCHAIN_TYPE,
    ],
    implementation = _gradle_build_impl,
)

def gradle_build(
        name = None,
        build_file = None,
        gradle_version = None,
        distribution = "@gradle//file",
        data = [],
        output_file = None,
        output_file_source = None,
        output_files = {},
        repos = [],
        tasks = ["build"],
        max_workers = 0,
        tags = []):
    output_file_destinations = []
    output_file_sources = []
    if (output_file):
        output_file_destinations += [output_file]
        if (output_file_source):
            output_file_sources += ["build/" + output_file_source]
        else:
            output_file_sources += ["build/" + output_file]
    for output_file_destination, output_file_source_name in output_files.items():
        output_file_destinations += [output_file_destination]
        output_file_sources += [output_file_source_name]

    _gradle_build_rule(
        name = name,
        build_file = build_file,
        data = data,
        distribution = distribution,
        output_file_sources = output_file_sources,
        output_file_destinations = output_file_destinations,
        output_log = name + ".log",
        repos = repos,
        tags = tags,
        tasks = tasks,
        max_workers = max_workers,
    )
