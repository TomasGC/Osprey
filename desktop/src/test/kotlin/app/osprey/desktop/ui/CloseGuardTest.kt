package app.osprey.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.osprey.jobs.JobOutcome
import app.osprey.jobs.JobQueue
import app.osprey.jobs.JobState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val POLL_MS = 5L
private const val STOPPING = "Stopping after the file it is on..."

@OptIn(ExperimentalTestApi::class)
class CloseGuardTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = JobQueue(scope)
    private val kept = AtomicInteger()
    private val quit = AtomicInteger()

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** A running apply that ends after the file it is on once it is asked to stop, or when [release] opens. */
    private fun stoppableJob(release: CountDownLatch) = jobs.enqueue("Reorg apply") { control ->
        while (!control.shouldStop() && !release.await(POLL_MS, TimeUnit.MILLISECONDS)) Unit
        JobOutcome("Reorg apply: done", stopped = control.shouldStop())
    }

    /** A running apply busy on one long file: it ignores a stop until [release] opens. */
    private fun busyJob(release: CountDownLatch) = jobs.enqueue("Reorg apply") {
        assertTrue(release.await(WAIT_MS, TimeUnit.MILLISECONDS))
        JobOutcome("Reorg apply: done")
    }

    private fun running() = jobs.entries.value.any { it.state is JobState.Running }

    private fun androidx.compose.ui.test.ComposeUiTest.showGuard() {
        setContent { CloseGuard(jobs, onKeep = { kept.incrementAndGet() }, onQuit = { quit.incrementAndGet() }) }
        waitUntil(timeoutMillis = WAIT_MS) { running() }
    }

    @Test
    fun `the dialog asks while a job runs and offers the three choices`() = runComposeUiTest {
        val release = CountDownLatch(1)
        stoppableJob(release)
        showGuard()

        onNodeWithText("A job is still running").assertExists()
        onNodeWithText("Keep running").assertExists()
        onNodeWithText("Stop and quit").assertExists()
        onNodeWithText("Quit now").assertExists()
        release.countDown()
    }

    @Test
    fun `keep running leaves the job running and quits nothing`() = runComposeUiTest {
        val release = CountDownLatch(1)
        stoppableJob(release)
        showGuard()

        onNodeWithText("Keep running").performClick()

        assertEquals(1, kept.get())
        assertEquals(0, quit.get())
        assertTrue(running())
        release.countDown()
    }

    @Test
    fun `quit now quits at once and leaves the job to be cut`() = runComposeUiTest {
        val release = CountDownLatch(1)
        stoppableJob(release)
        showGuard()

        onNodeWithText("Quit now").performClick()

        assertEquals(1, quit.get())
        assertEquals(0, kept.get())
        assertTrue(running())
        release.countDown()
    }

    @Test
    fun `stop and quit waits for the file it is on, then quits`() = runComposeUiTest {
        val release = CountDownLatch(1)
        busyJob(release)
        showGuard()

        onNodeWithText("Stop and quit").performClick()

        onNodeWithText(STOPPING).assertExists()
        assertEquals(0, quit.get())
        release.countDown()
        waitUntil(timeoutMillis = WAIT_MS) { quit.get() == 1 }
        assertEquals(0, kept.get())
    }

    @Test
    fun `stop and quit removes the jobs that were still waiting`() = runComposeUiTest {
        val release = CountDownLatch(1)
        stoppableJob(release)
        val waiting = jobs.enqueue("Trash delete") { JobOutcome("never runs") }
        showGuard()

        onNodeWithText("Stop and quit").performClick()

        waitUntil(timeoutMillis = WAIT_MS) { quit.get() == 1 }
        assertTrue(runBlocking { waiting.await() }.stopped)
    }

    @Test
    fun `a job that ends by itself withdraws the question and does not quit`() = runComposeUiTest {
        val release = CountDownLatch(1)
        stoppableJob(release)
        showGuard()

        release.countDown()

        waitUntil(timeoutMillis = WAIT_MS) { kept.get() == 1 }
        assertEquals(0, quit.get())
    }
}
