package app.osprey.android.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.OspreyApplication
import app.osprey.jobs.JobState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

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

    @Ignore("#112: Android drops the update that follows a job ending; the test returns with the pacing")
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

    private fun held(name: String, proveWatched: Boolean = true): HeldJob =
        HeldJob(app, probe, name, proveWatched).enqueue().also { jobs += it }
}
