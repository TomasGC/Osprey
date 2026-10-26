package app.osprey.jobs

/** A [JobControl] for code that runs outside any job (a test, a script): never stops, shows nothing. */
object NoJobControl : JobControl {

    override fun shouldStop(): Boolean = false

    override fun report(done: Int, total: Int, current: String) = Unit
}
