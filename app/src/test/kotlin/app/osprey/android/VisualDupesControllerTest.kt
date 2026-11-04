package app.osprey.android

import app.osprey.actions.trash.TrashLocation
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.jobs.JobState
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisualDupesControllerTest {

    private val fs = FakeFileSystemRepository()
    private val reader = FakeMediaMetadataReader()
    private val year = fs.addFolder(fs.root, "2019")
    private lateinit var controller: VisualDupesController
    private val history = InMemoryJobHistoryStore()
    private val jobs = immediateJobQueue(history)

    @Before
    fun setUp() {
        controller = VisualDupesController(fs, reader, jobs) { TrashLocation(fs.root, "2019") }
    }

    private fun picture(name: String): FileHandle {
        val handle = fs.addFile(year, name, name.toByteArray())
        reader.setFrame(handle, PixelBuffer(SIDE, SIDE, IntArray(SIDE * SIDE) { it * STEP }))
        return handle
    }

    private fun scanTwoCopies(): VisualDupeGroupUi {
        picture("a.jpg")
        picture("bb.jpg")
        controller.onScan(year)
        return controller.groupsIn(year).single()
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
        picture("a.jpg")
        picture("bb.jpg")
        picture("ccc.jpg")
        val flaky = VisualDupesController(FirstMoveFails(fs), reader, jobs) { TrashLocation(fs.root, "2019") }
        flaky.onScan(year)
        val group = flaky.groupsIn(year).single()
        group.keep["a.jpg"] = true
        group.keep["bb.jpg"] = false
        group.keep["ccc.jpg"] = false

        flaky.moveUnchecked(year, group)

        assertTrue(group.status.startsWith("Moved 1, failed 1 ("))
        assertFalse(group.done)
        val outcome = (jobs.entries.value.last().state as JobState.Finished).outcome
        assertEquals("Visual Dupes group #${group.index}: moved 1, 1 failed", outcome.summary)
        assertEquals(1, outcome.failures)
    }

    private fun names(folder: FileHandle) = fs.list(folder).map { it.name }.toSet()

    private fun folder(vararg path: String): FileHandle {
        var current: FileHandle = fs.root
        for (segment in path) current = fs.list(current).single { it.isDirectory && it.name == segment }.handle
        return current
    }

    @Test
    fun `a scan groups the look-alike images of the folder it ran in`() {
        val group = scanTwoCopies()

        assertEquals(setOf("a.jpg", "bb.jpg"), group.members.map { it.name }.toSet())
        assertFalse(controller.scanning)
    }

    private fun scanAndMoveSecond(scope: String?): VisualDupeGroupUi {
        picture("a.jpg")
        picture("bb.jpg")
        controller.onScan(year, scope)
        val group = controller.groupsIn(year).single()
        group.keep["a.jpg"] = true
        group.keep["bb.jpg"] = false
        controller.moveUnchecked(year, group)
        return group
    }

    @Test
    fun `with a scope the scan and a group move are named after it`() {
        val group = scanAndMoveSecond("2019")

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
        val stopping = object : MediaMetadataReader by reader {
            override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? {
                jobs.stop(jobs.entries.value.last { it.state is JobState.Running }.id)
                return reader.decodeFrame(entry, maxSide)
            }
        }
        val stopped = VisualDupesController(fs, stopping, jobs) { TrashLocation(fs.root, "2019") }
        picture("a.jpg")
        picture("bb.jpg")

        stopped.onScan(year, "2019")

        val job = jobs.entries.value.last().state as JobState.Finished
        assertEquals("Visual Dupes 2019 scan: stopped", job.outcome.summary)
    }

    @Test
    fun `the files of a group move are recorded under the year that was scanned`() {
        scanAndMoveSecond("2019")

        val run = history.runs().first { "group" in it.action }

        assertEquals(listOf("2019/bb.jpg"), run.files.map { it.path })
    }

    @Test
    fun `without a scope the files of a group move are recorded as they are`() {
        scanAndMoveSecond(null)

        val run = history.runs().first { "group" in it.action }

        assertEquals(listOf("bb.jpg"), run.files.map { it.path })
    }

    @Test
    fun `without a scope the jobs keep their plain names`() {
        val group = scanAndMoveSecond(null)

        val names = listOf("Visual Dupes scan", "Visual Dupes group #${group.index}")
        assertEquals(names, jobs.entries.value.map { it.name })
    }

    @Test
    fun `the groups are not handed out for any other folder`() {
        scanTwoCopies()

        assertTrue(controller.groupsIn(fs.root).isEmpty())
    }

    @Test
    fun `moving the unticked image sends it to the Trash and marks the group done`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["bb.jpg"] = false

        controller.moveUnchecked(year, group)

        assertEquals(setOf("a.jpg"), names(year))
        assertEquals(setOf("bb.jpg"), names(folder("Trash", "2019", "VisualDupes")))
        assertTrue(group.done)
        assertEquals("Moved 1", group.status)
        assertFalse(group.moving)
    }

    @Test
    fun `with nothing ticked nothing moves and the group says to keep one`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = false
        group.keep["bb.jpg"] = false

        controller.moveUnchecked(year, group)

        assertEquals("Keep at least one image.", group.status)
        assertEquals(setOf("a.jpg", "bb.jpg"), names(year))
    }

    @Test
    fun `with everything ticked nothing moves`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["bb.jpg"] = true

        controller.moveUnchecked(year, group)

        assertEquals("Nothing unchecked -- nothing to move.", group.status)
        assertFalse(group.done)
    }

    @Test
    fun `a group that is done is not moved again`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["bb.jpg"] = false
        controller.moveUnchecked(year, group)
        fs.addFile(year, "bb.jpg", "back again".toByteArray())

        controller.moveUnchecked(year, group)

        assertEquals(setOf("a.jpg", "bb.jpg"), names(year))
    }

    @Test
    fun `a file that is already gone is reported as skipped`() {
        val group = scanTwoCopies()
        group.keep["a.jpg"] = true
        group.keep["bb.jpg"] = false
        fs.delete(group.members.single { it.name == "bb.jpg" }.handle)

        controller.moveUnchecked(year, group)

        assertEquals("Moved 0 (skipped: bb.jpg)", group.status)
    }

    private companion object {
        const val SIDE = 8
        const val STEP = 3
    }
}
