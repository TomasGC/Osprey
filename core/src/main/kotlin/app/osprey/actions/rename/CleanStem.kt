package app.osprey.actions.rename

// Priority order matters: each pattern is tried in turn, first match wins. Ported from the
// POC's SUFFIX_PATTERNS verbatim (`(?P<name>...)` -> `(?<name>...)` is the only syntax change
// Python's `re` -> `kotlin.text.Regex` needs here).
private val copySuffixRegex = Regex("""^(?<base>.+?)\s*-\s*[Cc]opy(?:\s*\((?<n>\d+)\))?$""")
private val copyWordRegex = Regex("""^(?<base>.+?)\s+[Cc]opy(?:\s+(?<n>\d+))?$""")
private val parenRegex = Regex("""^(?<base>.+?)\s*\((?<n>\d+)\)$""")

// Deliberately single-digit only ([0-9], no quantifier): a multi-digit underscore suffix
// (`_12`, `DSC_0067`) is a real camera/sequence filename, not sync/OS copy noise, and must
// never be stripped. This is a product rule, not an implementation detail — see #4.
private val underscoreRegex = Regex("""^(?<base>.+?)_(?<n>[0-9])$""")

private val suffixPatterns = listOf(copySuffixRegex, copyWordRegex, parenRegex, underscoreRegex)

private fun stripSuffixOnce(stem: String): String? =
    suffixPatterns.firstNotNullOfOrNull { it.find(stem)?.groups?.get("base")?.value }

/**
 * Strips sync/OS duplicate-copy suffixes from [stem] — ` (N)`, `_N` (single digit only),
 * ` - Copy`, ` copy N` — up to [maxPasses] times, so stacked suffixes (`photo (1) (2)`) fully
 * resolve. Returns [stem] unchanged if no pattern matches, or once a pass makes no further
 * progress.
 */
fun cleanStem(stem: String, maxPasses: Int = 3): String {
    var result = stem
    repeat(maxPasses) {
        val stripped = stripSuffixOnce(result) ?: return result
        if (stripped == result) return result
        result = stripped
    }
    return result
}
