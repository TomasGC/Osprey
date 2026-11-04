package app.osprey.jobs

import app.osprey.history.FileRecord

/** A [JobControl] for code that runs outside any job (a test, a script): never stops, shows nothing. */
object NoJobControl : JobControl {

    override fun shouldStop(): Boolean = false

    override fun report(done: Int, total: Int, current: String) = Unit

    override fun record(file: FileRecord) = Unit

    override val failedFiles: Int = 0
}
