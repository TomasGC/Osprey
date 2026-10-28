package app.osprey.viewer

import app.osprey.actions.dedupe.DedupePlanner
import app.osprey.actions.trash.TrashLocation
import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.media.ExifOrientation
import app.osprey.media.JpegOrientation
import app.osprey.media.jpegWith
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a saved turn (#61) does to the other actions: it changes one byte of a file, in place, and nothing else. */
class SavedTurnEffectsTest {

    private val fs = FakeFileSystemRepository()
    private val year = fs.addFolder(fs.root, "2019")
    private val saver = TurnSaver(fs)

    @Test
    fun `a photo saved turned is no longer a byte twin for Dedupe`() {
        val a = fs.addFile(year, "a.jpg", jpegWith(orientation = 1))
        fs.addFile(year, "b.jpg", jpegWith(orientation = 1))
        val planner = DedupePlanner(fs)
        assertEquals(1, planner.plan(year, TrashLocation(fs.root, "2019")).moves.size)

        saver.save(a, "a.jpg", 1)

        assertEquals(emptyList<Any>(), planner.plan(year, TrashLocation(fs.root, "2019")).moves)
    }

    @Test
    fun `a saved turn keeps the file's handle and name, and changes only its orientation`() {
        val original = jpegWith(orientation = 1)
        val a = fs.addFile(year, "a.jpg", original)

        saver.save(a, "a.jpg", 1)

        assertEquals(listOf("a.jpg"), fs.list(year).map { it.name })
        assertEquals(ExifOrientation.ROTATE_90, JpegOrientation.read(fs.readBytes(a)))
        assertEquals(original.size, fs.readBytes(a).size)
    }
}
