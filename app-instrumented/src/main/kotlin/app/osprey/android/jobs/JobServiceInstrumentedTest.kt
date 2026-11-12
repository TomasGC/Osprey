package app.osprey.android.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.OspreyApplication
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val HOLD_MS = 15_000L

/**
 * The job service with the app's real queue and the app out of sight (#94). Each test uses names of its own, so the
 * jobs earlier tests left in the queue never match. A job is held on a latch until the test lets it end.
 */
@RunWith(AndroidJUnit4::class)
class JobServiceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as OspreyApplication
    private val probe = NotificationProbe(context)
    private val releaseFirst = CountDownLatch(1)
    private val releaseSecond = CountDownLatch(1)

    @Before
    fun setUp() {
        probe.prepare()
        assertFalse("a screen of the app is showing", app.isVisible)
    }

    @After
    fun tearDown() {
        releaseFirst.countDown()
        releaseSecond.countDown()
        probe.waitFor("the queue to empty") { app.jobs.entries.value.none { it.state !is JobState.Finished } }
        probe.clean()
    }

    @Test
    fun aJobThatEndsInTheBackgroundLeavesItsResult() {
        app.jobs.enqueue("Ends alone") { holdUntil(releaseFirst) }
        probe.waitFor("the service to show the job") { "Ends alone" in probe.ongoingTitles() }

        releaseFirst.countDown()

        probe.waitFor("its result") { "Ends alone" in probe.resultTitles() }
    }

    @Test
    fun aJobThatStartsClearsTheResultsOfEarlierJobsAndRunsAlone() {
        app.jobs.enqueue("Earlier") { holdUntil(releaseFirst) }
        probe.waitFor("the service to show the earlier job") { "Earlier" in probe.ongoingTitles() }
        releaseFirst.countDown()
        probe.waitFor("the result of the earlier job") { "Earlier" in probe.resultTitles() }

        app.jobs.enqueue("Later") { holdUntil(releaseSecond) }

        probe.waitFor("the later job alone") {
            probe.ongoingTitles() == listOf("Later") && probe.resultTitles().isEmpty()
        }
    }

    @Test
    fun aResultStaysWhileAJobThatWasAlreadyWaitingRuns() {
        app.jobs.enqueue("First") { holdUntil(releaseFirst) }
        app.jobs.enqueue("Second") { holdUntil(releaseSecond) }
        // While another job waits, the running notification's title says so: "First (+1 waiting)".
        probe.waitFor("the first job running") { probe.ongoingTitles().any { it?.startsWith("First") == true } }

        releaseFirst.countDown()

        probe.waitFor("the first result beside the second job") {
            "First" in probe.resultTitles() && "Second" in probe.ongoingTitles()
        }
    }

    private fun holdUntil(latch: CountDownLatch): JobOutcome {
        latch.await(HOLD_MS, TimeUnit.MILLISECONDS)
        return JobOutcome("done")
    }
}
