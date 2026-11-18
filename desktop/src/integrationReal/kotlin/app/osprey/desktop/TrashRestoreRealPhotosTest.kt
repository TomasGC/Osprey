package app.osprey.desktop

import app.osprey.actions.trash.TrashRestorer
import app.osprey.actions.trash.TrashScanner
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.fixtures.Photos
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

private const val YEAR = "2019"
private const val NAME = "IMG_001.jpg"

/**
 * A Trash restore of two same-name files on real photos (#85): two different photos trashed under one name by two
 * actions come back together, the later one suffixed, with every byte they had in the Trash.
 */
class TrashRestoreRealPhotosTest : TimedTest() {

    private val fs = DesktopFileSystemRepository()
    private lateinit var libraryDir: Path
    private lateinit var photos: List<Path>

    @Before
    fun setUp() {
        photos = Photos.jpeg(Photos.need()).take(2)
        libraryDir = Files.createTempDirectory("osprey-trash-restore-real-photos")
    }

    @After
    fun tearDown() {
        if (::libraryDir.isInitialized) libraryDir.toFile().deleteRecursively()
    }

    private fun trash(action: String, photo: Path) {
        val folder = Files.createDirectories(libraryDir.resolve("Trash/$YEAR/$action"))
        Files.copy(photo, folder.resolve(NAME))
    }

    @Test
    fun `two real photos trashed under one name come back byte for byte, the second suffixed`() {
        assertEquals(2, photos.size)
        trash("Dedupe", photos[0])
        trash("VisualDupes", photos[1])
        val root = PathHandle(libraryDir)
        val restorer = TrashRestorer(fs)

        val result = restorer.restore(root, restorer.plan(root, TrashScanner(fs).scan(root).entries))

        assertEquals(2, result.restored)
        assertArrayEquals(Files.readAllBytes(photos[0]), Files.readAllBytes(libraryDir.resolve("$YEAR/$NAME")))
        assertArrayEquals(
            Files.readAllBytes(photos[1]),
            Files.readAllBytes(libraryDir.resolve("$YEAR/IMG_001_VisualDupes.jpg")),
        )
        assertFalse(Files.exists(libraryDir.resolve("Trash/$YEAR")))
    }
}
