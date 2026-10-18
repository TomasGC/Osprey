package app.osprey.actions

/**
 * Splits [name] into (stem, suffix) exactly like Python's `pathlib.Path`: the suffix is the
 * last `.`-delimited extension, including the dot. A name with no dot, or whose only dot is its
 * first character (e.g. `.bashrc`), has no suffix.
 */
fun splitStemAndSuffix(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
}
