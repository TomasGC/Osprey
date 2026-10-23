package app.osprey.actions.trash

/**
 * The files ticked in the Trash screen, across all its year and action tabs. Entries are named by
 * their [TrashEntry.label] (`Trash/2019/Dedupe/a.jpg`), which stays the same when the Trash is read
 * again, so a selection survives a refresh.
 */
data class TrashSelection(val labels: Set<String> = emptySet()) {

    val isEmpty: Boolean get() = labels.isEmpty()

    operator fun contains(entry: TrashEntry): Boolean = entry.label in labels

    fun toggle(entry: TrashEntry): TrashSelection =
        TrashSelection(if (entry.label in labels) labels - entry.label else labels + entry.label)

    /** Whether every one of [tab] is selected; an empty tab never is. */
    fun allSelected(tab: List<TrashEntry>): Boolean = tab.isNotEmpty() && tab.all { it in this }

    /** Selects all of [tab], or unselects them all when they already were. */
    fun toggleAll(tab: List<TrashEntry>): TrashSelection {
        val names = tab.map { it.label }
        return TrashSelection(if (allSelected(tab)) labels - names.toSet() else labels + names)
    }

    /** The selected files that are still in [contents], in its order. */
    fun entriesIn(contents: TrashContents): List<TrashEntry> = contents.entries.filter { it in this }

    /** This selection without the files [contents] no longer holds. */
    fun retainedIn(contents: TrashContents): TrashSelection =
        TrashSelection(labels intersect contents.entries.map { it.label }.toSet())
}
