package app.osprey.desktop

import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.visual.VisualDupePlanner
import app.osprey.actions.visual.moveUncheckedToTrash
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.uprightDesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.fixtures.Photos
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A capture whose PNG, JPEG and HEIC all hash alike, well inside the threshold on any decoder. */
private const val BURST_SUBJECT = "sun_1"

/**
 * Visual Dupes on the real photos (#76). Bursts of one scene are distinct shots whose hashes sit far apart (14 to 38 of
 * 64 bits, against a threshold of 2), so they must never group; the same capture as a HEIC, a JPEG, a PNG and a JPEG
 * stored sideways under an Exif tag must group, and two different subjects must stay apart however they are stored.
 */
class VisualDupesRealPhotosTest : TimedTest() {

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
        if (::folder.isInitialized) folder.toFile().deleteRecursively()
    }

    private fun place(photo: Path) = Files.copy(photo, folder.resolve(photo.fileName.toString()))

    private fun groups() =
        planner.plan(PathHandle(folder)).groups.map { group -> group.map { it.name }.toSet() }.toSet()

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

    private fun placeAs(photo: Path, name: String) = Files.copy(photo, folder.resolve(name))

    /** One capture as a PNG and a JPEG, named as two frames of a burst by a tool that lowercases names (#141). */
    private fun placeLowercaseBurst(): Path {
        placeAs(fixtures.resolve("png").resolve("$BURST_SUBJECT.png"), "img_0000_burst1.png")
        placeAs(fixtures.resolve("jpg").resolve("$BURST_SUBJECT.jpg"), "img_0001_burst1.jpg")
        return fixtures.resolve("heic").resolve("$BURST_SUBJECT.heic")
    }

    @Test
    fun `two lowercase burst frames of the same picture are not grouped`() {
        placeLowercaseBurst()

        assertEquals(emptySet<Set<String>>(), groups())
    }

    @Test
    fun `lowercase burst frames bridged by a third copy are a burst set and both stay on disk`() {
        place(placeLowercaseBurst())
        val logs = mutableListOf<String>()

        val plan = planner.plan(PathHandle(folder))
        planner.describe(plan, logs::add)

        assertTrue(logs.toString(), logs.any { it.contains("BURST SET, keeping all 2 frames") })
        val keep = toUiGroups(plan.groups).single().keep.filterValues { it }.keys
        val library = Files.createTempDirectory("osprey-visual-dupes-burst-trash")
        try {
            val trash = TrashLocation(PathHandle(library), "2019")
            val group = plan.groups.single()
            moveUncheckedToTrash(DesktopFileSystemRepository(), trash, PathHandle(folder), group, keep)
        } finally {
            library.toFile().deleteRecursively()
        }
        val left = Files.list(folder).use { files -> files.map { it.fileName.toString() }.toList().toSet() }
        assertEquals(setOf("img_0000_burst1.png", "img_0001_burst1.jpg"), left)
    }
}
