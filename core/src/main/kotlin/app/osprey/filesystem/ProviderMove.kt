package app.osprey.filesystem

/**
 * The steps of a move to another folder under another name, on a provider that moves a file under its current name
 * only (#132). The new name is taken before the move, or after it through a temporary one when the source folder
 * already has it.
 *
 * @param renameSource Renames the file while it is in the source folder.
 * @param move Moves it into the destination folder.
 * @param renameMoved Renames it once it is in the destination folder.
 */
class ProviderMove(
    private val renameSource: (String) -> Unit,
    private val move: () -> Unit,
    private val renameMoved: (String) -> Unit,
) {

    /**
     * Moves the file called [name] so it ends as [target]. A move that fails leaves it in the source folder under
     * [name], or says under which name when [name] cannot be given back; a last rename that fails leaves it moved, and
     * the error says under which name. [takenInSource] says
     * whether another file of the source folder has [target]; it is only asked when the name changes.
     */
    fun run(name: String, target: String, takenInSource: () -> Boolean) {
        val during = if (target != name && takenInSource()) temporaryName(target) else target
        if (during != name) renameSource(during)
        var moved = false
        try {
            move()
            moved = true
        } finally {
            if (!moved && during != name) giveBack(name, during)
        }
        if (during == target) return
        val failure = failureReason { renameMoved(target) }
        check(failure == null) { "moved, but kept the name $during: $failure" }
    }

    private fun giveBack(name: String, during: String) {
        val failure = failureReason { renameSource(name) }
        check(failure == null) { "move failed and the file is left as $during: $failure" }
    }
}
