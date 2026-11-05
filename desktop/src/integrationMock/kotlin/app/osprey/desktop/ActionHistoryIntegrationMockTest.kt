package app.osprey.desktop

import app.osprey.actions.trash.TrashLocation
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.history.FileResult
import app.osprey.history.RunState
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every action through its real controller, real job queue, real files and a real history on disk (#79). Each test
 * reads the run back the way a new launch would, so what the History shows is what was written to disk.
 */
class ActionHistoryIntegrationMockTest : TimedTest() {

    private val harness = ActionHarness()

    @After
    fun tearDown() = harness.delete()

    private fun applyRun(action: String) = harness.runsAfterRestart().single { it.action == action }

    @Test
    fun `Flatten moves the files up and the history names each file under its year`() {
        harness.file("sub/a.jpg")
        harness.file("sub/deep/b.jpg")
        val launch = harness.launch()

        launch.scanAndApply(flattenController(harness.fs, launch.jobs))

        assertTrue(Files.exists(harness.year.resolve("a.jpg")))
        assertTrue(Files.exists(harness.year.resolve("b.jpg")))
        assertFalse(Files.exists(harness.year.resolve("sub").resolve("a.jpg")))
        val run = applyRun("Flatten 2021 apply")
        assertEquals(RunState.Done, run.state)
        assertEquals(
            listOf("2021/sub/a.jpg" to "moved up as a.jpg", "2021/sub/deep/b.jpg" to "moved up as b.jpg"),
            run.files.sortedBy { it.path }.map { it.path to it.message },
        )
        assertEquals(setOf(FileResult.Done), run.files.map { it.result }.toSet())
    }

    @Test
    fun `Rename strips the suffix and the history records the name and size before and after`() {
        harness.file("photo (1).jpg", "twelve bytes")
        val launch = harness.launch()

        launch.scanAndApply(renameController(harness.fs, launch.jobs))

        assertTrue(Files.exists(harness.year.resolve("photo.jpg")))
        assertFalse(Files.exists(harness.year.resolve("photo (1).jpg")))
        val record = applyRun("Rename 2021 apply").files.single()
        assertEquals(FileResult.Done, record.result)
        assertEquals("photo (1).jpg", record.before?.name)
        assertEquals("photo.jpg", record.after?.name)
        assertEquals(12L, record.before?.size)
        assertEquals(12L, record.after?.size)
    }

    @Test
    fun `Standardize renames from the filename and the history records the old and the new name`() {
        harness.file("download_20160828_193034.jpeg", "photo")
        val launch = harness.launch()

        launch.scanAndApply(standardizeController(harness.fs, harness.reader, launch.jobs))

        assertTrue(Files.exists(harness.year.resolve("IMG_2016-08-28_19-30-34.jpeg")))
        val record = applyRun("Standardize 2021 apply").files.single()
        assertEquals(FileResult.Done, record.result)
        assertEquals("download_20160828_193034.jpeg", record.before?.name)
        assertEquals("IMG_2016-08-28_19-30-34.jpeg", record.after?.name)
        assertEquals(5L, record.after?.size)
    }

    @Test
    fun `Dates sets the modified date from the filename and the history records it before and after`() {
        val photo = harness.file("IMG_20170425_152602.jpg", "photo")
        val launch = harness.launch()

        launch.scanAndApply(datesController(harness.fs, harness.reader, launch.jobs))

        val expected = LocalDateTime.of(2017, 4, 25, 15, 26, 2).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(expected.toEpochMilli(), Files.getLastModifiedTime(photo).toMillis())
        val record = applyRun("Dates 2021 apply").files.single()
        assertEquals(FileResult.Done, record.result)
        assertEquals(expected, record.after?.modified)
        assertNotEquals(expected, record.before?.modified)
        assertEquals(5L, record.before?.size)
    }

    @Test
    fun `Dedupe moves the copy to the Trash and the history says which file it duplicated`() {
        harness.file("a.jpg", "same bytes")
        harness.file("bb.jpg", "same bytes")
        val launch = harness.launch()
        val dedupe = dedupeController(harness.fs, launch.jobs) { TrashLocation(PathHandle(harness.library), "2021") }

        launch.scanAndApply(dedupe)

        assertTrue(Files.exists(harness.year.resolve("a.jpg")))
        assertFalse(Files.exists(harness.year.resolve("bb.jpg")))
        val trashed = Files.walk(harness.library.resolve("Trash")).use { it.filter { p -> p.fileName.toString() == "bb.jpg" }.toList() }
        assertEquals(1, trashed.size)
        val record = applyRun("Dedupe 2021 apply").files.single()
        assertEquals("2021/bb.jpg", record.path)
        assertTrue(record.message.endsWith("(same as a.jpg)"))
    }

    @Test
    fun `a file that fails is listed as failed and the files after it are still processed`() {
        harness.file("sub/a.jpg")
        harness.file("sub/b.jpg")
        harness.file("sub/c.jpg")
        val launch = harness.launch()

        launch.scanAndApply(flattenController(FailsToMove(harness.fs, "b.jpg"), launch.jobs))

        assertTrue(Files.exists(harness.year.resolve("a.jpg")))
        assertTrue(Files.exists(harness.year.resolve("c.jpg")))
        assertTrue(Files.exists(harness.year.resolve("sub").resolve("b.jpg")))
        val run = applyRun("Flatten 2021 apply")
        assertEquals(RunState.Failed, run.state)
        assertEquals(
            listOf("2021/sub/b.jpg" to FileResult.Failed),
            run.files.filter { it.result == FileResult.Failed }.map { it.path to it.result },
        )
        assertEquals(2, run.files.count { it.result == FileResult.Done })
    }

    @Test
    fun `a run the app died in is Interrupted after a restart and keeps the files it had finished`() {
        harness.file("sub/a.jpg")
        val first = harness.launch()
        first.scanAndApply(flattenController(harness.fs, first.jobs))
        File(harness.historyDir, "99.jsonl").writeText(
            """{"t":"run","id":99,"action":"Flatten 2021 scan","at":1793737437979}""" + "\n" +
                """{"t":"start","at":1793737437986}""" + "\n" +
                """{"t":"file","path":"2021/sub/x.jpg","result":"Done","message":"moved up as x.jpg"}""" + "\n",
        )

        val runs = harness.runsAfterRestart()

        val interrupted = runs.single { it.state == RunState.Interrupted }
        assertEquals(99, interrupted.id)
        assertEquals(listOf("2021/sub/x.jpg"), interrupted.files.map { it.path })
        assertEquals(2, runs.count { it.state == RunState.Done })
    }
}
