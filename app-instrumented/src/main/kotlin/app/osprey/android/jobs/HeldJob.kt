package app.osprey.android.jobs

import app.osprey.OspreyApplication
import app.osprey.jobs.JobControl
import app.osprey.jobs.JobOutcome
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val HOLD_MS = 30_000L
private const val POLL_MS = 100L
private const val WATCHED_FILE = "watched.jpg"
private const val REPORT_EVERY_MS = 500L
private const val REPORT_TOTAL = 1_000

/**
 * A job that stays running until the test ends it (or Stop does). With [proveWatched] it first proves the job service
 * is watching the queue: the service posts its first notification before it starts to watch, and only the watcher
 * writes a job's progress into it, so a notification that shows the progress means the watcher is running (#94, #108).
 * Without that proof a job can end before the watcher starts, and its result is never posted. The progress repeats
 * while the job is held, so the proof does not hang on one update.
 */
class HeldJob(
    private val app: OspreyApplication,
    private val probe: NotificationProbe,
    val name: String,
    private val proveWatched: Boolean = true,
) {
    private val reported = CountDownLatch(if (proveWatched) 1 else 0)
    private val release = CountDownLatch(1)

    fun enqueue(): HeldJob {
        app.jobs.enqueue(name) { run(it) }
        return this
    }

    /** Waits until the service is showing this job and watching the queue. */
    fun waitUntilWatched() {
        probe.waitFor("the service to show $name") { probe.ongoingTitles().any { it?.startsWith(name) == true } }
        reported.countDown()
        probe.waitFor("the service to watch $name") { probe.ongoingTexts().any { it?.contains(WATCHED_FILE) == true } }
    }

    /** Lets the job end. */
    fun end() = release.countDown()

    /** Lets the job end at once, for a test's cleanup. */
    fun abandon() = release.countDown()

    private fun run(control: JobControl): JobOutcome {
        reported.await(HOLD_MS, TimeUnit.MILLISECONDS)
        val limit = System.currentTimeMillis() + HOLD_MS
        var done = 0
        var lastReport = 0L
        while (!control.shouldStop() && release.count > 0 && System.currentTimeMillis() < limit) {
            val now = System.currentTimeMillis()
            if (proveWatched && now - lastReport >= REPORT_EVERY_MS) {
                control.report(done++, REPORT_TOTAL, WATCHED_FILE)
                lastReport = now
            }
            Thread.sleep(POLL_MS)
        }
        return JobOutcome("done", stopped = control.shouldStop())
    }
}
