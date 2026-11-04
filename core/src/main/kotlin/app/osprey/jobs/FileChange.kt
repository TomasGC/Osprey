package app.osprey.jobs

import app.osprey.filesystem.failureReason
import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.FileState

/** The two states of one file around a change: what it looked like [before] and [after] (#79). */
class FileChange {
    var before: FileState? = null
    var after: FileState? = null
}

/**
 * Runs [change] on the file called [name], records how it went and returns true when it worked (#79). Only what a
 * file system can throw at a file counts as a failure (see [failureReason]); a bug still propagates.
 */
fun JobControl.applyToFile(name: String, change: FileChange.() -> Unit): Boolean {
    val file = FileChange()
    val failure = failureReason { file.change() }
    record(
        FileRecord(
            name,
            if (failure == null) FileResult.Done else FileResult.Failed,
            failure.orEmpty(),
            file.before,
            file.after,
        ),
    )
    return failure == null
}

/**
 * Records the file called [path] after an action that has its own failure handling: [failure] is why it failed, or
 * null when it worked, in which case [doneMessage] says what was done (#79).
 */
fun JobControl.recordResult(path: String, failure: String?, doneMessage: String) {
    record(FileRecord(path, if (failure == null) FileResult.Done else FileResult.Failed, failure ?: doneMessage))
}

/**
 * Runs [change] on the file called [path] and records how it went (#79): done with [doneMessage], or failed with the
 * reason. A file that fails does not stop the run: it is recorded and the next file goes on, so the History lists what
 * worked and what did not. Returns true when it worked. Only what a file system can throw at a file counts as a
 * failure (see [failureReason]); a bug still propagates.
 */
fun JobControl.tryFile(path: String, doneMessage: String, change: () -> Unit): Boolean {
    val failure = failureReason(change)
    recordResult(path, failure, doneMessage)
    return failure == null
}
