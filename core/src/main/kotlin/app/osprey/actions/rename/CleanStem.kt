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

// The `base` group is the first group of every pattern above.
private const val BASE_GROUP = 1

private fun stripSuffixOnce(stem: String): String? =
    suffixPatterns.firstNotNullOfOrNull { pattern -> pattern.find(stem)?.let { it.groupValues[BASE_GROUP] } }

/**
 * Strips sync/OS duplicate-copy suffixes from [stem] — ` (N)`, `_N` (single digit only),
 * ` - Copy`, ` copy N` — up to [maxPasses] times, so stacked suffixes (`photo (1) (2)`) fully
 * resolve. Returns [stem] unchanged if no pattern matches.
 */
fun cleanStem(stem: String, maxPasses: Int = 3): String {
    var result = stem
    repeat(maxPasses) {
        result = stripSuffixOnce(result) ?: return result
    }
    return result
}
