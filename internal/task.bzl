"""Defines Bazel rules for interacting with Gradle."""

GRADLE_WRAPPER = "@rules_gradle//gradle/wrapper"
GRADLE_TOOL_RUNNER = "@rules_gradle//java:runner"
JAVA_TOOLCHAIN_TYPE = "@bazel_tools//tools/jdk:runtime_toolchain_type"

_WrapperArgument = struct(
    PROJECT = "gradle_project",
    DISTRIBUTION = "gradle_distribution",
    BUILDFILE = "gradle_build_file",
    JAVA_HOME = "java_home",
    LOGFILE = "logfile",
    OUTPUT = "output",
)

def _arg(args, argument, value = None):
    val = argument
    if value != None:
        val = "--%s=%s" % (argument, value)
    args.add(val)

def _gradle_task_impl(ctx):
    """Run a Gradle task and capture a build output."""

    # resolve the runner, declare a file to capture run output
    runner = ctx.attr.runner
    inputs, _, _ = ctx.resolve_command(
        tools = [runner],
    )
    tool_bin = inputs[0]
    tool_path = tool_bin.path

    direct_inputs = []
    transitive_inputs = []

    # resolve java toolchains
    java = ctx.toolchains[JAVA_TOOLCHAIN_TYPE].java_runtime
    transitive_inputs.append(java.files)

    # resolve project and wrapper inputs
    project = ctx.attr.project.files.to_list()
    direct_inputs += project

    if ctx.attr.data:
        direct_inputs += ctx.attr.data

    if ctx.attr.distribution:
        direct_inputs += ctx.files.distribution

    wrapper = ctx.attr._wrapper.files.to_list()
    direct_inputs += wrapper

    tasklist = ctx.attr.tasks
    project_root = project[0].dirname

    env = {}
    outputs = []
    args = ctx.actions.args()

    # prepare required arguments first
    _arg(args, _WrapperArgument.PROJECT, project_root)
    _arg(args, _WrapperArgument.BUILDFILE, ctx.file.build_file.path)
    _arg(args, _WrapperArgument.LOGFILE, ctx.outputs.output_log.path)
    _arg(args, _WrapperArgument.DISTRIBUTION, ctx.attr.distribution.files.to_list()[0].path)
    _arg(args, _WrapperArgument.JAVA_HOME, java.java_home)

    # gradle outputs to the `build` directory within the project root
    outputs.append(ctx.actions.declare_directory(
        "build",
    ))

    if ctx.attr.outputs != None and len(ctx.attr.outputs) > 0:
        for out in ctx.attr.outputs:
            if not out.startswith("build/"):
                out = "build/%s" % out
            outfile = ctx.actions.declare_file(out)
            outputs.append(outfile)
            _arg(args, _WrapperArgument.OUTPUT, "%s=%s" % (out, outfile.path))

    # add each requisite task to the positional arguments list at the end
    for i in tasklist:
        args.add(i)

    # write our arguments file, which we will pass to the wrapper
    marker = ctx.actions.declare_file(
        "bazel-gradle-build.args",
    )
    ctx.actions.write(
        marker,
        args,
        is_executable = False,
    )

    # prepare list of inputs for gradle build execution
    inputs = depset(
        direct_inputs,
        transitive = transitive_inputs,
    )

    # run gradle through the wrapper
    ctx.actions.run(
        inputs = inputs,
        outputs = outputs + [ctx.outputs.output_log],
        executable = ctx.executable.runner,
        progress_message = "Gradle %s: %s" % (ctx.label, ", ".join(tasklist)),
        mnemonic = "GradleTask",
        arguments = [args],
        env = env,
        use_default_shell_env = True,
        toolchain = JAVA_TOOLCHAIN_TYPE,
        tools = [
            ctx.executable.runner,
        ],
    )

    return [DefaultInfo(
        files = depset(outputs),
        runfiles = ctx.runfiles(
            collect_data = True,
            collect_default = True,
            files = [],
        ),
    )]

_gradle_task_rule = rule(
    implementation = _gradle_task_impl,
    attrs = {
        "runner": attr.label(
            cfg = "exec",
            default = Label(GRADLE_TOOL_RUNNER),
            allow_files = True,
            executable = True,
        ),
        "output_log": attr.output(),
        "_wrapper": attr.label(
            default = GRADLE_WRAPPER,
            allow_files = True,
        ),
        "build_file": attr.label(
            allow_single_file = True,
        ),
        "project": attr.label(
            allow_files = True,
        ),
        "outputs": attr.string_list(
            mandatory = True,
        ),
        "tasks": attr.string_list(
            mandatory = False,
            default = ["build"],
        ),
        "debug": attr.bool(
            default = False,
        ),
        "distribution": attr.label(
            allow_files = True,
            default = Label("@gradle//file"),
        ),
        "data": attr.label_list(allow_files = True),
    },
    fragments = [
        "java",
    ],
    toolchains = [
        JAVA_TOOLCHAIN_TYPE,
    ],
)

def _gradle_task(name, build_file = "build.gradle.kts", tags = [], output_log = None, **kwargs):
    """ Macro to wrap a Gradle task target. """

    _gradle_task_rule(
        name = name,
        build_file = build_file,
        output_log = output_log or (name + ".log"),
        tags = tags + [
            "requires-network",
        ],
        **kwargs
    )

gradle_task = _gradle_task
