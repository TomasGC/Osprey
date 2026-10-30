package app.osprey.desktop

import app.osprey.actions.dedupe.DedupePlanner
import app.osprey.actions.trash.TrashLocation
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.fixtures.Photos
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val YEAR = "2019"
private const val COPY_SUFFIX = "_copy"

/**
 * Dedupe on the real photos (#76): the byte-hash planner must trash an exact copy of a photo and nothing else. The
 * look-alikes (bursts of one scene, some with nearly the same size) differ in their bytes, so none of them may be
 * taken for a duplicate; that is what the grouping by size and then by hash has to get right.
 */
class DedupeRealPhotosTest {

    private val fs = DesktopFileSystemRepository()
    private lateinit var libraryDir: Path
    private lateinit var yearDir: Path
    private lateinit var originals: List<Path>

    @Before
    fun setUp() {
        originals = Photos.heic(Photos.need())
        libraryDir = Files.createTempDirectory("osprey-dedupe-real-photos")
        yearDir = Files.createDirectory(libraryDir.resolve(YEAR))
        for (photo in originals) Files.copy(photo, yearDir.resolve(photo.fileName.toString()))
    }

    @After
    fun tearDown() {
        if (::libraryDir.isInitialized) libraryDir.toFile().deleteRecursively()
    }

    private fun copyName(original: Path) = original.fileName.toString().replace(".heic", "$COPY_SUFFIX.heic")

    private fun planner() = DedupePlanner(fs)

    private val trash get() = TrashLocation(PathHandle(libraryDir), YEAR)

    @Test
    fun `the look-alike photos of a library are not duplicates of each other`() {
        val plan = planner().plan(PathHandle(yearDir), trash)

        assertEquals(emptyList<String>(), plan.moves.map { it.sourceName })
    }

    @Test
    fun `a copy with one byte changed has the same size but is not a duplicate`() {
        val original = originals.first()
        val bytes = Files.readAllBytes(original)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        Files.write(yearDir.resolve(copyName(original).replace(COPY_SUFFIX, "_edit")), bytes)

        val plan = planner().plan(PathHandle(yearDir), trash)

        assertEquals(emptyList<String>(), plan.moves.map { it.sourceName })
    }

    @Test
    fun `an exact copy of each photo is trashed, the original stays, and no look-alike goes with it`() {
        for (photo in originals) Files.copy(photo, yearDir.resolve(copyName(photo)))

        val plan = planner().plan(PathHandle(yearDir), trash)

        assertEquals(originals.map(::copyName).sorted(), plan.moves.map { it.sourceName }.sorted())
        for (move in plan.moves) assertEquals(move.sourceName.replace(COPY_SUFFIX, ""), move.keepName)

        assertEquals(originals.size, planner().apply(plan, trash))
        val dedupe = libraryDir.resolve("Trash").resolve(YEAR).resolve("Dedupe")
        assertEquals(originals.map(::copyName).sorted(), Files.list(dedupe).use { l -> l.map { it.fileName.toString() }.sorted().toList() })
        for (photo in originals) {
            assertArrayEquals(Files.readAllBytes(photo), Files.readAllBytes(yearDir.resolve(photo.fileName.toString())))
            assertTrue(Files.notExists(yearDir.resolve(copyName(photo))))
        }
    }
}
