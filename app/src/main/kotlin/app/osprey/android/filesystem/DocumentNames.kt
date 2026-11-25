package app.osprey.android.filesystem

import androidx.documentfile.provider.DocumentFile
import app.osprey.filesystem.ProviderRename

// The shared storage takes names that differ only by case for one name, and `findFile` compares them exactly (#132).

/** The child called [name], or else one whose name differs only by case. */
internal fun DocumentFile.findIgnoringCase(name: String): DocumentFile? {
    val children = listFiles().map { it to it.name }
    val found = children.firstOrNull { it.second == name }
        ?: children.firstOrNull { it.second.equals(name, ignoreCase = true) }
    return found?.first
}

/** Whether a child other than [document] is called [name], in any case. */
internal fun DocumentFile.hasOther(name: String, document: DocumentFile): Boolean =
    listFiles().any { it.uri != document.uri && it.name.equals(name, ignoreCase = true) }

/** Renames this document, never accepting another name than the one asked for. */
internal fun DocumentFile.renaming(): ProviderRename = ProviderRename({ name.orEmpty() }, ::renameTo)
