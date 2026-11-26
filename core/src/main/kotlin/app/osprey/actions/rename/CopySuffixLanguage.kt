package app.osprey.actions.rename

/**
 * How one language writes the suffix of a copied file: ` - <word>` and ` - <word> (n)`. A new language is one line in
 * [copySuffixLanguages].
 *
 * @property word the word for "copy", matched in any case.
 * @property spacesAroundDash false only where the word joined by a bare dash is still a copy suffix. The safe default
 * is true: `photo-copie` is French for a photocopy.
 * @property bareWord true where the suffix is also written without the dash: ` <word>` and ` <word> n`.
 */
class CopySuffixLanguage(val word: String, val spacesAroundDash: Boolean = true, val bareWord: Boolean = false)

/** The languages Rename knows. Add one only with the forms a system really writes. */
val copySuffixLanguages = listOf(
    // English, as ported from the POC.
    CopySuffixLanguage("copy", spacesAroundDash = false, bareWord = true),
    // French (#143).
    CopySuffixLanguage("copie"),
)
