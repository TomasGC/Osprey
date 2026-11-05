package app.osprey.desktop

import app.osprey.actions.trash.TrashLocation
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.jobs.JobState
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisualDupesControllerTest : TimedTest() {

    /** Every file decodes to the same small picture, so any two images are visual duplicates. */
    private object SamePictureReader : MediaMetadataReader {
        override fun captureDateTime(entry: FileHandle): MediaDate? = null

        override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer =
            PixelBuffer(SIDE, SIDE, IntArray(SIDE * SIDE) { it * STEP })
    }

    private lateinit var library: Path
    private lateinit var year: Path
    private lateinit var controller: VisualDupesController
    private val history = InMemoryJobHistoryStore()
    private val jobs = immediateJobQueue(history)

    private val yearDir get() = PathHandle(year)

    @Before
    fun setUp() {
        library = Files.createTempDirectory("osprey-visual-controller-test")
        year = Files.createDirectory(library.resolve("2019"))
        controller = VisualDupesController(
            DesktopFileSystemRepository(),
            SamePictureReader,
            jobs,
        ) { TrashLocation(PathHandle(library), "2019") }
    }

    @After
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    private fun scanTwoCopies(): VisualDupeGroupUi {
        Files.writeString(year.resolve("a.jpg"), "aaaa")
        Files.writeString(year.resolve("b.jpg"), "bb")
        controller.onScan(yearDir)
        return controller.groupsIn(yearDir).single()
    }

    /** A file system whose first move fails like a locked file, and later ones work. */
    private class FirstMoveFails(private val inner: FileSystemRepository) : FileSystemRepository by inner {
        private var failed = false

        override fun move(entry: FileHandle, destFolder: FileHandle, newName: String?): FileHandle {
            if (!failed) {
                failed = true
                throw IOException("locked")
            }
            return inner.move(entry, destFolder, newName)
        }
    }

    @Test
    fun `a member that could not be moved is reported, the others move and the group can be tried again`() {
        listOf("a" to "aaaa", "b" to "bb", "c" to "c").forEach { (name, text) ->
            Files.writeString(year.resolve("$name.jpg"), text)
        }
        val flaky = VisualDupesController(FirstMoveFails(DesktopFileSystemRepository()), SamePictureReader, jobs) {
            TrashLocation(PathHandle(library), "2019")
        }
        flaky.onScan(yearDir)
        val group = flaky.groupsIn(yearDir).single()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false
        group.keep["c.jpg"] = false

        flaky.moveUnchecked(yearDir, group)

        assertTrue(group.status.startsWith("Moved 1, failed 1 ("))
        assertFalse(group.done)
        val outcome = (jobs.entries.value.last().state as JobState.Finished).outcome
        assertEquals("Visual Dupes group #${group.index}: moved 1, 1 failed", outcome.summary)
        assertEquals(1, outcome.failures)
    }

    @Test
    fun `a scan groups the look-alike images of the folder it ran in`() {
        val group = scanTwoCopies()

        assertEquals(setOf("a.jpg", "b.jpg"), group.members.map { it.name }.toSet())
        assertFalse(controller.scanning)
    }

    private fun scanAndMoveB(scope: String?): VisualDupeGroupUi {
        Files.writeString(year.resolve("a.jpg"), "aaaa")
        Files.writeString(year.resolve("b.jpg"), "bb")
        controller.onScan(yearDir, scope)
        val group = controller.groupsIn(yearDir).single()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false
        controller.moveUnchecked(yearDir, group)
        return group
    }

    @Test
    fun `with a scope the scan and a group move are named after it`() {
        val group = scanAndMoveB("2019")

        val names = listOf("Visual Dupes 2019 scan", "Visual Dupes 2019 group #${group.index}")
        assertEquals(names, jobs.entries.value.map { it.name })
        val summaries = jobs.entries.value.map { (it.state as JobState.Finished).outcome.summary }
        assertEquals(
            listOf("Visual Dupes 2019 scan: 1 groups", "Visual Dupes 2019 group #${group.index}: moved 1"),
            summaries,
        )
    }

    @Test
    fun `a stopped scan with a scope names it in its summary`() {
        val stopping = object : MediaMetadataReader by SamePictureReader {
            override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer {
                jobs.stop(jobs.entries.value.last { it.state is JobState.Running }.id)
                return SamePictureReader.decodeFrame(entry, maxSide)
            }
        }
        val stopped = VisualDupesController(DesktopFileSystemRepository(), stopping, jobs) {
            TrashLocation(PathHandle(library), "2019")
        }
        Files.writeString(year.resolve("a.jpg"), "aaaa")
        Files.writeString(year.resolve("b.jpg"), "bb")

        stopped.onScan(yearDir, "2019")

        val job = jobs.entries.value.last().state as JobState.Finished
        assertEquals("Visual Dupes 2019 scan: stopped", job.outcome.summary)
    }

    @Test
    fun `the files of a group move are recorded under the year that was scanned`() {
        scanAndMoveB("2019")

        val run = history.runs().first { "group" in it.action }

        assertEquals(listOf("2019/b.jpg"), run.files.map { it.path })
    }

    @Test
    fun `without a scope the files of a group move are recorded as they are`() {
        scanAndMoveB(null)

        val run = history.runs().first { "group" in it.action }

        assertEquals(listOf("b.jpg"), run.files.map { it.path })
    }

    @Test
    fun `without a scope the jobs keep their plain names`() {
        val group = scanAndMoveB(null)

        val names = listOf("Visual Dupes scan", "Visual Dupes group #${group.index}")
        assertEquals(names, jobs.entries.value.map { it.name })
    }

    @Test
    fun `the groups are not handed out for any other folder`() {
        scanTwoCopies()

        assertTrue(controller.groupsIn(PathHandle(library)).isEmpty())
    }

    @Test
    fun `moving the unticked image sends it to the Trash and marks the group done`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false

        controller.moveUnchecked(yearDir, group)

        assertTrue(Files.exists(year.resolve("a.jpg")))
        assertTrue(Files.exists(library.resolve("Trash").resolve("2019").resolve("VisualDupes").resolve("b.jpg")))
        assertTrue(group.done)
        assertEquals("Moved 1", group.status)
        assertFalse(group.moving)
    }

    @Test
    fun `with nothing ticked nothing moves and the group says to keep one`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = false
        group.keep["b.jpg"] = false

        controller.moveUnchecked(yearDir, group)

        assertEquals("Keep at least one image.", group.status)
        assertTrue(Files.exists(year.resolve("b.jpg")))
    }

    @Test
    fun `with everything ticked nothing moves`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = true

        controller.moveUnchecked(yearDir, group)

        assertEquals("Nothing unchecked -- nothing to move.", group.status)
        assertFalse(group.done)
    }

    @Test
    fun `a group that is done is not moved again`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false
        controller.moveUnchecked(yearDir, group)
        Files.writeString(year.resolve("b.jpg"), "back again")

        controller.moveUnchecked(yearDir, group)

        assertTrue(Files.exists(year.resolve("b.jpg")))
    }

    @Test
    fun `a file that is already gone is reported as skipped`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["b.jpg"] = false
        Files.delete(year.resolve("b.jpg"))

        controller.moveUnchecked(yearDir, group)

        assertEquals("Moved 0 (skipped: b.jpg)", group.status)
    }

    private companion object {
        const val SIDE = 8
        const val STEP = 3
    }
}
