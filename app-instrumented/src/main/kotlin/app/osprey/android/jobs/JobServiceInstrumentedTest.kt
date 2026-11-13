package app.osprey.android.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.OspreyApplication
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val SWEEP_MS = 700L
private const val SWEEP_STEP_MS = 10L

/**
 * The job service with the app's real queue and the app out of sight (#94). Each test uses names of its own, so the
 * jobs earlier tests left in the queue never match. A [HeldJob] stays running until the test lets it end.
 */
@RunWith(AndroidJUnit4::class)
class JobServiceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as OspreyApplication
    private val probe = NotificationProbe(context)
    private val jobs = mutableListOf<HeldJob>()

    @Before
    fun setUp() {
        probe.prepare()
        assertFalse("a screen of the app is showing", app.isVisible)
    }

    @After
    fun tearDown() {
        jobs.forEach { it.abandon() }
        probe.waitFor("the queue to empty") { app.jobs.entries.value.none { it.state !is JobState.Finished } }
        probe.clean()
    }

    @Test
    fun aJobThatEndsInTheBackgroundLeavesItsResult() {
        val job = held("Ends alone")
        job.waitUntilWatched()

        job.end()

        probe.waitFor("its result") { "Ends alone" in probe.resultTitles() }
    }

    @Test
    fun aJobThatStartsClearsTheResultsOfEarlierJobsAndRunsAlone() {
        val earlier = held("Earlier")
        earlier.waitUntilWatched()
        earlier.end()
        probe.waitFor("the result of the earlier job") { "Earlier" in probe.resultTitles() }

        held("Later", proveWatched = false)

        probe.waitFor("the later job alone") {
            probe.ongoingTitles() == listOf("Later") && probe.resultTitles().isEmpty()
        }
    }

    @Test
    fun aResultStaysWhileAJobThatWasAlreadyWaitingRuns() {
        val first = held("First")
        held("Second", proveWatched = false)
        first.waitUntilWatched()

        first.end()

        probe.waitFor("the first result beside the second job") {
            "First" in probe.resultTitles() && "Second" in probe.ongoingTitles()
        }
    }

    /** A job that returns at once ends before the service has begun to watch the queue (#108). */
    @Test
    fun aJobThatEndsAtOnceStillLeavesItsResult() {
        app.jobs.enqueue("Quick") { JobOutcome("done") }

        probe.waitFor("its result") { "Quick" in probe.resultTitles() }
    }

    @Test
    fun aResultAlreadyAnnouncedIsNotAnnouncedAgain() {
        app.jobs.enqueue("Announced") { JobOutcome("done") }
        probe.waitFor("the first result") { "Announced" in probe.resultTitles() }
        probe.waitFor("the service to stop") { !probe.jobServiceRunning() }
        probe.clean()

        app.jobs.enqueue("Later quick") { JobOutcome("done") }

        probe.waitFor("the second result") { "Later quick" in probe.resultTitles() }
        assertEquals(listOf<String?>("Later quick"), probe.resultTitles())
    }

    /**
     * The window is a few milliseconds wide and sits a little after the job ends (the job notices at its next poll, the
     * service posts its result, then stops), so the next job is queued at every delay across it (#111). A job that gets
     * no service shows no running notification, or kills the app with `ForegroundServiceDidNotStartInTimeException`,
     * which ends the whole run.
     */
    @Test
    fun aJobQueuedWhileTheServiceStopsGetsAService() {
        for (delayMs in 0L..SWEEP_MS step SWEEP_STEP_MS) {
            probe.prepare()
            val last = held("Last $delayMs")
            last.waitUntilWatched()
            last.end()
            Thread.sleep(delayMs)

            val next = held("Next $delayMs", proveWatched = false)

            probe.waitFor("the running notification of Next $delayMs") { "Next $delayMs" in probe.ongoingTitles() }
            next.end()
        }
    }

    private fun held(name: String, proveWatched: Boolean = true): HeldJob =
        HeldJob(app, probe, name, proveWatched).enqueue().also { jobs += it }
}
