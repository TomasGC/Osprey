package app.osprey.filesystem

import app.osprey.actions.splitStemAndSuffix
import java.util.UUID

private const val TEMPORARY_MARKER = ".osprey-rename-"

/** A name no file has, keeping [name]'s extension so a file left under it is still recognised. */
fun temporaryName(name: String): String {
    val (stem, suffix) = splitStemAndSuffix(name)
    return "$stem$TEMPORARY_MARKER${UUID.randomUUID()}$suffix"
}

/**
 * Renames one document through a provider that may choose another name than the one asked and still say it worked
 * (#132): Android's shared storage gives `name (1)` for a taken name, and counts the document's own name, so a name
 * that differs only by case has to go through a temporary one.
 *
 * @param currentName The document's name now.
 * @param renameTo Asks the provider for a new name; false when it refuses.
 */
class ProviderRename(private val currentName: () -> String, private val renameTo: (String) -> Boolean) {

    /**
     * Gives the document exactly [newName], or throws and leaves it under the name it had:
     * [FileAlreadyExistsException] when the provider chose another name, [IllegalStateException] when it refused.
     * When even the old name cannot be given back, the [IllegalStateException] says under which name the file is.
     */
    fun to(newName: String) {
        val original = currentName()
        if (original == newName || !original.equals(newName, ignoreCase = true)) return step(newName)
        step(temporaryName(newName))
        try {
            step(newName)
        } catch (e: FileAlreadyExistsException) {
            giveBack(original, e)
        } catch (e: IllegalStateException) {
            giveBack(original, e)
        }
    }

    private fun giveBack(original: String, failure: Exception): Nothing {
        renameTo(original)
        val left = currentName()
        check(left == original) { "${failure.message}; the file is left as $left" }
        throw failure
    }

    private fun step(name: String) {
        val before = currentName()
        check(renameTo(name)) { "rename failed: $before -> $name" }
        val given = currentName()
        if (given != name) {
            renameTo(before)
            throw FileAlreadyExistsException("$name is taken: the storage gave $given instead")
        }
    }
}
