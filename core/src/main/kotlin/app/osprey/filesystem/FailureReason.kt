package app.osprey.filesystem

import java.io.IOException

/**
 * Runs [action] and returns why it failed, or null when it worked. Only what a file system can throw at
 * a file operation counts as a failure; any other exception is a bug and propagates.
 */
fun failureReason(action: () -> Unit): String? = try {
    action()
    null
} catch (e: IOException) {
    e.reason()
} catch (e: FileAlreadyExistsException) {
    e.reason()
} catch (e: DirectoryNotEmptyException) {
    e.reason()
} catch (e: IllegalStateException) {
    e.reason()
} catch (e: SecurityException) {
    e.reason()
}

private fun Throwable.reason(): String = message.orEmpty()
