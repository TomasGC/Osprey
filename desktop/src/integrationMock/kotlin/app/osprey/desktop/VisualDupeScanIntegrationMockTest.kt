package app.osprey.desktop

import app.osprey.actions.trash.TrashLocation
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.history.RunState
import app.osprey.jobs.JobState
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

private const val WAIT_SECONDS = 20L
private const val PHOTOS = 12
private const val SIDE = 8

/** The scan through its real controller, job queue and history, decoding several files at once (#87). */
class VisualDupeScanIntegrationMockTest : TimedTest() {

    private val harness = ActionHarness()
    private val workers = scanWorkers()

    @After
    fun tearDown() = harness.delete()

    /** Decodes block until [release]; [allBusy] opens once as many run as the scan has workers. */
    private inner class HeldReader : MediaMetadataReader {
        val allBusy = CountDownLatch(workers)
        val release = CountDownLatch(1)
        val started = AtomicInteger()
        val ended = AtomicInteger()

        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer {
            started.incrementAndGet()
            allBusy.countDown()
            check(release.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "never released" }
            ended.incrementAndGet()
            return PixelBuffer(SIDE, SIDE, IntArray(SIDE * SIDE) { it })
        }
    }

    private fun controller(launch: ActionHarness.Launch, reader: MediaMetadataReader) =
        VisualDupesController(harness.fs, reader, launch.jobs, { harness.libraryLabel }) {
            TrashLocation(PathHandle(harness.library), "2021")
        }

    private fun photos() = repeat(PHOTOS) { harness.file("photo$it.jpg") }

    @Test
    fun `a scan decodes as many files at once as it has workers`() {
        photos()
        val launch = harness.launch()
        val reader = HeldReader()
        val visual = controller(launch, reader)

        visual.onScan(harness.yearHandle(), "2021")
        assertEquals(true, reader.allBusy.await(WAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(workers, reader.started.get())
        reader.release.countDown()
        launch.awaitJobs()

        assertEquals(PHOTOS, reader.ended.get())
        assertEquals(PHOTOS, visual.groupsIn(harness.yearHandle()).single().members.size)
    }

    @Test
    fun `Stop ends the scan once the files being decoded are done, and the History says stopped`() {
        photos()
        val launch = harness.launch()
        val reader = HeldReader()
        val visual = controller(launch, reader)

        visual.onScan(harness.yearHandle(), "2021")
        assertEquals(true, reader.allBusy.await(WAIT_SECONDS, TimeUnit.SECONDS))
        launch.jobs.stop(launch.jobs.entries.value.single { it.state is JobState.Running }.id)
        reader.release.countDown()
        launch.awaitJobs()

        assertEquals(workers, reader.started.get())
        assertEquals(workers, reader.ended.get())
        assertEquals(emptyList<VisualDupeGroupUi>(), visual.groupsIn(harness.yearHandle()))
        val run = harness.runsAfterRestart().single()
        assertEquals(RunState.Stopped, run.state)
        assertEquals("Visual Dupes 2021 scan: stopped", run.summary)
    }
}
