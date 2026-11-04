package app.osprey.jobs

import app.osprey.history.FileRecord
import app.osprey.history.FileResult

/** A [JobControl] that remembers every report and asks to stop once [stopAfterReports] files were started. */
class RecordingJobControl(private val stopAfterReports: Int = Int.MAX_VALUE) : JobControl {

    val reports = mutableListOf<JobProgress>()
    val files = mutableListOf<FileRecord>()

    override fun shouldStop(): Boolean = reports.size >= stopAfterReports

    override fun report(done: Int, total: Int, current: String) {
        reports += JobProgress(done, total, current)
    }

    override val failedFiles: Int get() = files.count { it.result == FileResult.Failed }

    override fun record(file: FileRecord) {
        files += file
    }
}
