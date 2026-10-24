package app.osprey.jobs

/** A [JobControl] that remembers every report and asks to stop once [stopAfterReports] files were started. */
class RecordingJobControl(private val stopAfterReports: Int = Int.MAX_VALUE) : JobControl {

    val reports = mutableListOf<JobProgress>()

    override fun shouldStop(): Boolean = reports.size >= stopAfterReports

    override fun report(done: Int, total: Int, current: String) {
        reports += JobProgress(done, total, current)
    }
}
