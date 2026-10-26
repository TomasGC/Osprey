package app.osprey.jobs

/**
 * What a running job sees: a way to report which file it is on, and whether it was asked to stop. A job
 * checks [shouldStop] between two files, never inside one, so a file is finished or untouched.
 */
interface JobControl {

    /** True once a stop was requested; the job ends after the file it is on. */
    fun shouldStop(): Boolean

    /** Tells whoever watches that the job is starting file number [done] + 1 of [total], called [current]. */
    fun report(done: Int, total: Int, current: String)
}
