package app.osprey.desktop

import app.osprey.actions.visual.VisualDupePlanner
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.uprightDesktopMediaMetadataReader
import app.osprey.fixtures.Photos
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Visual Dupes on the real photos (#76). Bursts of one scene are distinct shots whose hashes sit far apart (14 to 38 of
 * 64 bits, against a threshold of 2), so they must never group; the same capture as a HEIC, a JPEG, a PNG and a JPEG
 * stored sideways under an Exif tag must group, and two different subjects must stay apart however they are stored.
 */
class VisualDupesRealPhotosTest {

    private val planner = VisualDupePlanner(DesktopFileSystemRepository(), uprightDesktopMediaMetadataReader())
    private lateinit var folder: Path
    private lateinit var fixtures: Path

    @Before
    fun setUp() {
        fixtures = Photos.need()
        folder = Files.createTempDirectory("osprey-visual-dupes-real-photos")
    }

    @After
    fun tearDown() {
        folder.toFile().deleteRecursively()
    }

    private fun place(photo: Path) = Files.copy(photo, folder.resolve(photo.fileName.toString()))

    private fun groups() = planner.plan(PathHandle(folder)).groups.map { group -> group.map { it.name }.toSet() }.toSet()

    @Test
    fun `the burst shots of a scene are never grouped, nor are different subjects`() {
        Photos.heic(fixtures).forEach(::place)

        assertEquals(emptySet<Set<String>>(), groups())
    }

    @Test
    fun `one capture stored as a HEIC, a JPEG, a PNG and a tagged JPEG is one group per subject`() {
        val pngs = Photos.png(fixtures)
        val subjects = pngs.map { it.fileName.toString().removeSuffix(".png") }
        for (subject in subjects) place(fixtures.resolve("heic").resolve("$subject.heic"))
        pngs.forEach(::place)
        Photos.jpeg(fixtures).forEach(::place)

        val expected = subjects.map { subject ->
            val tagged = Photos.jpeg(fixtures).map { it.fileName.toString() }.filter { it.startsWith("${subject}_tag") }
            (setOf("$subject.heic", "$subject.png", "$subject.jpg") + tagged)
        }.toSet()
        assertEquals(expected, groups())
    }
}
