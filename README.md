
# Gradle Rules for Bazel

[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-v1.4-ff69b4.svg)](CODE_OF_CONDUCT.md)
![Bazel 7](https://img.shields.io/badge/Bazel%207-black?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyBpZD0iTGF5ZXJfMiIgZGF0YS1uYW1lPSJMYXllciAyIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA0My41NyA0My4zNyI%2BCiAgPGRlZnM%2BCiAgICA8c3R5bGU%2BCiAgICAgIC5jbHMtMSB7CiAgICAgICAgZmlsbDogIzAwNDMwMDsKICAgICAgfQoKICAgICAgLmNscy0yIHsKICAgICAgICBmaWxsOiAjMDA3MDFhOwogICAgICB9CgogICAgICAuY2xzLTMgewogICAgICAgIGZpbGw6ICM0M2EwNDc7CiAgICAgIH0KCiAgICAgIC5jbHMtNCB7CiAgICAgICAgZmlsbDogIzc2ZDI3NTsKICAgICAgfQogICAgPC9zdHlsZT4KICA8L2RlZnM%2BCiAgPGcgaWQ9IkxheWVyXzEtMiIgZGF0YS1uYW1lPSJMYXllciAxIj4KICAgIDxwYXRoIGNsYXNzPSJjbHMtMiIgZD0ibTIxLjc4LDMyLjY4djEwLjY5bC0xMC44OS0xMC44OXYtMTAuNjlsMTAuODksMTAuODlaIi8%2BCiAgICA8cGF0aCBjbGFzcz0iY2xzLTEiIGQ9Im0yMS43OCwzMi42OGwxMC45LTEwLjg5djEwLjY5bC0xMC45LDEwLjg5di0xMC42OVoiLz4KICAgIDxwYXRoIGNsYXNzPSJjbHMtMyIgZD0ibTEwLjg5LDIxLjc5djEwLjY5TDAsMjEuNTh2LTEwLjY5bDEwLjg5LDEwLjlaIi8%2BCiAgICA8cGF0aCBjbGFzcz0iY2xzLTMiIGQ9Im00My41NywxMC44OXYxMC42OWwtMTAuODksMTAuOXYtMTAuNjlsMTAuODktMTAuOVoiLz4KICAgIDxwYXRoIGNsYXNzPSJjbHMtMyIgZD0ibTIxLjc4LDMyLjY4bC0xMC44OS0xMC44OSwxMC44OS0xMC45LDEwLjksMTAuOS0xMC45LDEwLjg5WiIvPgogICAgPHBhdGggY2xhc3M9ImNscy00IiBkPSJtMTAuODksMjEuNzlMMCwxMC44OSwxMC44OSwwbDEwLjg5LDEwLjg5LTEwLjg5LDEwLjlaIi8%2BCiAgICA8cGF0aCBjbGFzcz0iY2xzLTQiIGQ9Im0zMi42OCwyMS43OWwtMTAuOS0xMC45TDMyLjY4LDBsMTAuODksMTAuODktMTAuODksMTAuOVoiLz4KICA8L2c%2BCjwvc3ZnPg%3D%3D&logoColor=gray)
![Bzlmod](https://img.shields.io/badge/Bzlmod-black?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyBpZD0iTGF5ZXJfMiIgZGF0YS1uYW1lPSJMYXllciAyIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA0My41NyA0My4zNyI%2BCiAgPGRlZnM%2BCiAgICA8c3R5bGU%2BCiAgICAgIC5jbHMtMSB7CiAgICAgICAgZmlsbDogIzAwNDMwMDsKICAgICAgfQoKICAgICAgLmNscy0yIHsKICAgICAgICBmaWxsOiAjMDA3MDFhOwogICAgICB9CgogICAgICAuY2xzLTMgewogICAgICAgIGZpbGw6ICM0M2EwNDc7CiAgICAgIH0KCiAgICAgIC5jbHMtNCB7CiAgICAgICAgZmlsbDogIzc2ZDI3NTsKICAgICAgfQogICAgPC9zdHlsZT4KICA8L2RlZnM%2BCiAgPGcgaWQ9IkxheWVyXzEtMiIgZGF0YS1uYW1lPSJMYXllciAxIj4KICAgIDxwYXRoIGNsYXNzPSJjbHMtMiIgZD0ibTIxLjc4LDMyLjY4djEwLjY5bC0xMC44OS0xMC44OXYtMTAuNjlsMTAuODksMTAuODlaIi8%2BCiAgICA8cGF0aCBjbGFzcz0iY2xzLTEiIGQ9Im0yMS43OCwzMi42OGwxMC45LTEwLjg5djEwLjY5bC0xMC45LDEwLjg5di0xMC42OVoiLz4KICAgIDxwYXRoIGNsYXNzPSJjbHMtMyIgZD0ibTEwLjg5LDIxLjc5djEwLjY5TDAsMjEuNTh2LTEwLjY5bDEwLjg5LDEwLjlaIi8%2BCiAgICA8cGF0aCBjbGFzcz0iY2xzLTMiIGQ9Im00My41NywxMC44OXYxMC42OWwtMTAuODksMTAuOXYtMTAuNjlsMTAuODktMTAuOVoiLz4KICAgIDxwYXRoIGNsYXNzPSJjbHMtMyIgZD0ibTIxLjc4LDMyLjY4bC0xMC44OS0xMC44OSwxMC44OS0xMC45LDEwLjksMTAuOS0xMC45LDEwLjg5WiIvPgogICAgPHBhdGggY2xhc3M9ImNscy00IiBkPSJtMTAuODksMjEuNzlMMCwxMC44OSwxMC44OSwwbDEwLjg5LDEwLjg5LTEwLjg5LDEwLjlaIi8%2BCiAgICA8cGF0aCBjbGFzcz0iY2xzLTQiIGQ9Im0zMi42OCwyMS43OWwtMTAuOS0xMC45TDMyLjY4LDBsMTAuODksMTAuODktMTAuODksMTAuOVoiLz4KICA8L2c%2BCjwvc3ZnPg%3D%3D&logoColor=gray)

---

> Latest release: _(None yet)_

> **Important**
> Currently in **active development**. Feedback welcome but will probably break your build.

Use [Gradle](https://gradle.org) from [Bazel](https://bazel.build), with support for:

- Obtaining outputs built by Gradle from a Bazel build
- Building with Gradle within Bazel's sandbox
- Sandboxed Gradle home and build directories
- Provide sealed Maven dependency repositories
- Bazel-provided distribution of Gradle

## Installation

**Via `WORKSPACE.bazel`:**

```starlark
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
```

```starlark
http_archive(
    name = "rules_gradle",
    sha256 = "...",
    strip_prefix = "rules_gradle-<revision>",
    urls = [
        "https://github.com/sgammon/rules_gradle/archive/<revision>/rules_gradle-<revision>.zip",
    ],
)
```

```starlark
http_file(
    name = "gradle",
    sha256 = "591855b517fc635b9e04de1d05d5e76ada3f89f5fc76f87978d1b245b4f69225",
    url = "https://dl.less.build/toolchains/gradle/gradle-8.3-bin.zip",
)
```

> You can use any Gradle toolchain download URL, including those provided by Gradle, in the form:
> `https://services.gradle.org/distributions/gradle-<version>-<bin|all>.zip`

## Usage

**In a `BUILD.bazel` file:**

```starlark
load("@rules_gradle//gradle:defs.bzl", "gradle_task")

filegroup(
    name = "files",
    srcs = [
        "build.gradle.kts",
        "settings.gradle.kts",
        "src/main/java/HelloWorld.java",
    ],
)

gradle_task(
    name = "project",
    outputs = [
        "libs/sample-project.jar",
    ],
    project = ":files",
)
```

**Then, you can do:**

```
bazel build //path/to/your:project
```

**And obtain the outputs within the Bazel task graph:**

```
BUILD SUCCESSFUL in 639ms
2 actionable tasks: 2 executed
Target //e2e/project:project up-to-date:
  bazel-bin/e2e/project/build
  bazel-bin/e2e/project/build/libs/sample-project.jar

INFO: Elapsed time: 58.984s, Critical Path: 36.79s
INFO: 395 processes: 44 internal, 331 darwin-sandbox, 20 worker.
INFO: Build completed successfully, 395 total actions
```

## Roadmap

Stuff which is coming soon, time and APIs permitting, in rough order of priority:

- Support for Windows
- Support for Bzlmod
- Extension to `rules_jvm_external` for easily installing Gradle plugins
- Integration between Bazel's Java Toolchain and [Gradle Toolchains](https://docs.gradle.org/current/userguide/toolchains.html)
- Stubbed Coursier-resolved repos
- Dispatch of Gradle within the Bazel wrapper entrypoint
- Analysis-time task graph integration (not sure this is even possible)
- Gradle invocation with no daemon
- Gradle plugin for tighter integration with Bazel
- Support for Gradle invocations from Bazel persistent workers
- Hand Gradle specific paths for toolchains (for instance, Java toolchain integration)
- Native binary for wrapper (maybe)
