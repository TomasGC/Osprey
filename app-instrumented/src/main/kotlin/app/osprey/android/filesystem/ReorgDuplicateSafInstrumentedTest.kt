package app.osprey.android.filesystem

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.actions.reorg.DuplicateChoice
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.trash.TrashAction
import app.osprey.actions.trash.TrashRestorer
import app.osprey.actions.trash.TrashScanner
import app.osprey.jobs.NoJobControl
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.settings.InputMode
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val LIBRARY_FILE = "IMG_20180101_000000.jpg"
private const val INPUT_FILE = "IMG_20180101_000001.jpg"
private val PHOTO_BYTES = "same photo bytes".toByteArray()

/**
 * The Reorg duplicate review (#91) on a real Storage Access Framework tree: a Move input file whose content is already
 * in the library is offered as a pair, and the file the user unticks is set aside in `Trash/<year>/Reorg` by the same
  * calls the app makes (copy, verify, delete for the input file; a document move for the library file). The input and
  * the
 * library are two folders of one granted tree, so one provider: a trash across two providers is a phone check.
 */
@RunWith(AndroidJUnit4::class)
class ReorgDuplicateSafInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fs = AndroidFileSystemRepository(context)
    private val planner = ReorgPlanner(fs, FakeMediaMetadataReader())
    private var testRoot: DocumentFile? = null
    private lateinit var library: DocumentFile
    private lateinit var camera: DocumentFile

    @Before
    fun setUp() {
        testRoot = SafGrant.workFolder(context, "osprey_reorg_duplicates")
        assumeTrue(SafGrant.SKIP_REASON, testRoot != null)
        library = testRoot!!.createDirectory("Library") ?: error("could not create Library")
        camera = testRoot!!.createDirectory("Camera") ?: error("could not create Camera")
        seed(library.createDirectory("2018") ?: error("could not create 2018"), LIBRARY_FILE)
        seed(camera, INPUT_FILE)
    }

    @After
    fun tearDown() {
        testRoot?.delete()
    }

    private fun seed(parent: DocumentFile, name: String) {
        val doc = parent.createFile("image/jpeg", name) ?: error("could not create $name")
        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(PHOTO_BYTES) } ?: error(
            "could not write $name",
        )
    }

    private fun output() = AndroidFileHandle(library)

    private fun sources() = listOf(ReorgSource(AndroidFileHandle(camera), "Camera", InputMode.MOVE))

    private fun namesIn(folder: DocumentFile?): Set<String> =
        folder?.listFiles()?.mapNotNull { it.name }?.toSet().orEmpty()

    private fun trashReorg(): DocumentFile? = library.findFile("Trash")?.findFile("2018")?.findFile("Reorg")

    private fun bytesOf(file: DocumentFile?): ByteArray =
        context.contentResolver.openInputStream(checkNotNull(file).uri)?.use { it.readBytes() } ?: error("cannot read")

    private fun settle(keepInput: Boolean, keepLibrary: Boolean) {
        val plan = planner.plan(output(), sources())
        planner.apply(plan, output(), plan.duplicates.map { DuplicateChoice(it, keepInput, keepLibrary) }, NoJobControl)
    }

    @Test
    fun aMoveInputFileAlreadyInTheLibraryIsOfferedAsAPair() {
        val plan = planner.plan(output(), sources())

        assertEquals(emptyList<Any>(), plan.items)
        val pair = plan.duplicates.single()
        assertEquals("Camera/$INPUT_FILE", pair.input.label)
        assertEquals("2018/$LIBRARY_FILE", pair.library.label)
        assertEquals(PHOTO_BYTES.size.toLong(), pair.library.sizeBytes)
    }

    @Test
    fun anUntickedInputFileIsCopiedVerifiedAndDeletedIntoTheTrash() {
        settle(keepInput = false, keepLibrary = true)

        assertEquals(emptySet<String>(), namesIn(camera))
        assertArrayEquals(PHOTO_BYTES, bytesOf(library.findFile("2018")?.findFile(LIBRARY_FILE)))
        assertEquals(setOf(INPUT_FILE), namesIn(trashReorg()))
        assertArrayEquals(PHOTO_BYTES, bytesOf(trashReorg()?.findFile(INPUT_FILE)))
    }

    @Test
    fun anUntickedLibraryFileIsMovedIntoTheTrashAndTheInputFileStays() {
        settle(keepInput = true, keepLibrary = false)

        assertEquals(setOf(INPUT_FILE), namesIn(camera))
        assertNull(library.findFile("2018")?.findFile(LIBRARY_FILE))
        assertEquals(setOf(LIBRARY_FILE), namesIn(trashReorg()))
    }

    @Test
    fun twoTickedFilesChangeNothing() {
        settle(keepInput = true, keepLibrary = true)

        assertEquals(setOf(INPUT_FILE), namesIn(camera))
        assertEquals(setOf(LIBRARY_FILE), namesIn(library.findFile("2018")))
        assertNull(library.findFile("Trash"))
    }

    @Test
    fun aFileSetAsideComesBackFromTheTrash() {
        settle(keepInput = false, keepLibrary = true)
        val entries = TrashScanner(fs).scan(output()).entries("2018", TrashAction.REORG)
        assertNotNull(entries.singleOrNull())
        val restorer = TrashRestorer(fs)

        val result = restorer.restore(output(), restorer.plan(output(), entries))

        assertEquals(1, result.restored)
        assertEquals(setOf(LIBRARY_FILE, INPUT_FILE), namesIn(library.findFile("2018")))
        assertArrayEquals(PHOTO_BYTES, bytesOf(library.findFile("2018")?.findFile(INPUT_FILE)))
    }
}
