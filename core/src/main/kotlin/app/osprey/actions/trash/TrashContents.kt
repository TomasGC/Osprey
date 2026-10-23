package app.osprey.actions.trash

/** Everything in the Trash of a library, as [TrashScanner] found it. */
class TrashContents(val entries: List<TrashEntry>) {

    /** The years that hold something, ascending. */
    val years: List<String> get() = entries.map { it.year }.distinct().sorted()

    /** The actions that hold something in [year], in [TrashAction] order. */
    fun actions(year: String): List<TrashAction> =
        TrashAction.entries.filter { action -> entries.any { it.year == year && it.action == action } }

    /** The files in `Trash/[year]/[action]`, by name. */
    fun entries(year: String, action: TrashAction): List<TrashEntry> =
        entries.filter { it.year == year && it.action == action }.sortedBy { it.name }
}
