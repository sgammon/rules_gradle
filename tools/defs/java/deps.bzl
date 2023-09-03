"Provides Java dependency macros."

def _rewrite_maven_coordinate(string):
    return string.replace("-", "_").replace(":", "_").replace(".", "_")

def maven(spec = None, artifact = None, group = None, repo = "@maven"):
    """Utility to rewrite a regular Maven dependency coordinate to a target name.

    Args:
        spec: Single-string specification, like `some.group:artifact`. Mutually exclusive
          with `artifact` and `group`.
        artifact: Name of the artifact; used with `group`. Mutually exclusive with `spec`.
        group: Group of the artifact; used with `artifact`. Mutually exclusive with `spec`.
        repo: Repo name to use; defaults to `@maven`.

    Returns:
        Formatted target string.
    """

    coordinates = None

    if spec:
        coordinates = _rewrite_maven_coordinate(spec)

    elif artifact and group:
        coordinates = _rewrite_maven_coordinate("%s:%s" % (group, artifact))

    if coordinates == None:
        fail("Failed to calculate coordinates for Maven dependency")

    return "%s//:%s" % (repo, coordinates)
