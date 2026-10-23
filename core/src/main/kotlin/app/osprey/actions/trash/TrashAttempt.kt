package app.osprey.actions.trash

import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileAlreadyExistsException
import java.io.IOException

/** Runs [action] for the file called [label]; what a file system can throw at it becomes a [TrashFailure]. */
internal fun attempt(label: String, action: () -> Unit): TrashFailure? = try {
    action()
    null
} catch (e: IOException) {
    TrashFailure(label, e.message.orEmpty())
} catch (e: FileAlreadyExistsException) {
    TrashFailure(label, e.message.orEmpty())
} catch (e: DirectoryNotEmptyException) {
    TrashFailure(label, e.message.orEmpty())
} catch (e: IllegalStateException) {
    TrashFailure(label, e.message.orEmpty())
} catch (e: SecurityException) {
    TrashFailure(label, e.message.orEmpty())
}
