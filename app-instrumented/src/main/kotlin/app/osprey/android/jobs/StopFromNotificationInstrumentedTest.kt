package app.osprey.android.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.osprey.OspreyApplication
import app.osprey.jobs.JobState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Stop button of a running job as a person finds it: in the notification shade, collapsed, with no tap to
 * expand it first (#94). The shade is opened and the button tapped with UI Automator; the outcome is read from the
 * queue and the system's own notifications, never from the screen.
 */
@RunWith(AndroidJUnit4::class)
class StopFromNotificationInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as OspreyApplication
    private val probe = NotificationProbe(context)
    private val shade = NotificationShade(UiDevice.getInstance(instrumentation))
    private val jobs = mutableListOf<HeldJob>()

    @Before
    fun setUp() {
        probe.prepare()
        assertFalse("a screen of the app is showing", app.isVisible)
    }

    @After
    fun tearDown() {
        jobs.forEach { it.abandon() }
        app.jobs.entries.value.filter { it.state !is JobState.Finished }.forEach { app.jobs.stop(it.id) }
        probe.waitFor("the queue to empty") { app.jobs.entries.value.none { it.state !is JobState.Finished } }
        shade.close()
        probe.clean()
    }

    @Test
    fun theRunningJobCanBeStoppedFromTheCollapsedNotification() {
        held("Stop me", proveWatched = false)
        probe.waitFor("the running notification") { "Stop me" in probe.ongoingTitles() }

        shade.open()
        val stop = checkNotNull(shade.stopOf("Stop me")) {
            "No Stop on the collapsed notification; on screen: ${shade.texts()}"
        }
        stop.click()

        probe.waitFor("the job to end stopped") { finishedStopped("Stop me") }
        probe.waitFor("its result") { "Stop me" in probe.resultTitles() }
    }

    @Ignore("#114: One UI sometimes ranks the result card above the running card, hiding Stop")
    @Test
    fun theRunningJobKeepsItsStopWithAResultStackedBehindIt() {
        val first = held("Chain first")
        held("Chain second", proveWatched = false)
        first.waitUntilWatched()
        first.end()
        probe.waitFor("the first result beside the second job") {
            "Chain first" in probe.resultTitles() && "Chain second" in probe.ongoingTitles()
        }

        shade.open()
        val stop = checkNotNull(shade.stopOf("Chain second")) {
            "No Stop on the running job's card; on screen: ${shade.texts()}"
        }
        stop.click()

        probe.waitFor("the second job to end stopped") { finishedStopped("Chain second") }
    }

    private fun held(name: String, proveWatched: Boolean = true): HeldJob =
        HeldJob(app, probe, name, proveWatched).enqueue().also { jobs += it }

    private fun finishedStopped(name: String): Boolean {
        val state = app.jobs.entries.value.firstOrNull { it.name == name }?.state
        return state is JobState.Finished && state.outcome.stopped
    }
}
