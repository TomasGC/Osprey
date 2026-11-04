package app.osprey.jobs

import app.osprey.history.FileRecord

/**
 * What a running job sees: a way to report which file it is on, and whether it was asked to stop. A job
 * checks [shouldStop] between two files, never inside one, so a file is finished or untouched.
 */
interface JobControl {

    /** True once a stop was requested; the job ends after the file it is on. */
    fun shouldStop(): Boolean

    /** Tells whoever watches that the job is starting file number [done] + 1 of [total], called [current]. */
    fun report(done: Int, total: Int, current: String)

    /** Keeps [file], one file the job handled, in the run's history (#79); read later on the run page. */
    fun record(file: FileRecord)

    /** How many files recorded through [record] ended as failed; a job reports it as its failures. */
    val failedFiles: Int
}
