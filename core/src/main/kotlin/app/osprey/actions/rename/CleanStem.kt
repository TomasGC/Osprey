package app.osprey.actions.rename

// Priority order matters: each pattern is tried in turn, first match wins. The order is that of the POC's
// SUFFIX_PATTERNS (dash copy, word copy, parenthesis, underscore); the two copy shapes are built for each of
// [copySuffixLanguages].
private fun dashRegex(language: CopySuffixLanguage): Regex {
    val space = if (language.spacesAroundDash) """\s+""" else """\s*"""
    val word = Regex.escape(language.word)
    return Regex("""^(?<base>.+?)$space-$space$word(?:\s*\((?<n>\d+)\))?$""", RegexOption.IGNORE_CASE)
}

private fun wordRegex(language: CopySuffixLanguage) =
    Regex("""^(?<base>.+?)\s+${Regex.escape(language.word)}(?:\s+(?<n>\d+))?$""", RegexOption.IGNORE_CASE)

private val parenRegex = Regex("""^(?<base>.+?)\s*\((?<n>\d+)\)$""")

// Deliberately single-digit only ([0-9], no quantifier): a multi-digit underscore suffix
// (`_12`, `DSC_0067`) is a real camera/sequence filename, not sync/OS copy noise, and must
// never be stripped. This is a product rule, not an implementation detail — see #4.
private val underscoreRegex = Regex("""^(?<base>.+?)_(?<n>[0-9])$""")

private val suffixPatterns = copySuffixLanguages.map(::dashRegex) +
    copySuffixLanguages.filter { it.bareWord }.map(::wordRegex) +
    listOf(parenRegex, underscoreRegex)

// The `base` group is the first group of every pattern above.
private const val BASE_GROUP = 1

private fun stripSuffixOnce(stem: String): String? =
    suffixPatterns.firstNotNullOfOrNull { pattern -> pattern.find(stem)?.let { it.groupValues[BASE_GROUP] } }

/**
 * Strips sync/OS duplicate-copy suffixes from [stem] — ` (N)`, `_N` (single digit only), and the copy word of each of
 * [copySuffixLanguages], in any case — up to [maxPasses] times, so stacked suffixes (`photo (1) (2)`) fully resolve.
 * Returns [stem] unchanged if no pattern matches.
 */
fun cleanStem(stem: String, maxPasses: Int = 3): String {
    var result = stem
    repeat(maxPasses) {
        result = stripSuffixOnce(result) ?: return result
    }
    return result
}
