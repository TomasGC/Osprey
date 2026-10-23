package app.osprey.actions.trash

import app.osprey.filesystem.formatSize

/** The question asked before [entries] are deleted for good: scope, count, size, and that it is final. */
fun clearConfirmation(entries: Collection<TrashEntry>): String {
    val noun = if (entries.size == 1) "file" else "files"
    val size = formatSize(entries.sumOf { it.sizeBytes })
    return "Permanently delete ${entries.size} $noun ($size) from ${scopeOf(entries)}? This cannot be undone."
}

private fun scopeOf(entries: Collection<TrashEntry>): String {
    val years = entries.map { it.year }.distinct()
    return when {
        years.size > 1 -> "the Trash, across ${years.size} years"
        years.isEmpty() -> "the Trash"
        entries.map { it.action }.distinct().size == 1 -> "Trash/${years.single()}/${entries.first().action.folderName}"
        else -> "Trash/${years.single()}"
    }
}
