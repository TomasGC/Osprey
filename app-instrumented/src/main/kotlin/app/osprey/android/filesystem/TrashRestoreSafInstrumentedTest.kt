package app.osprey.android.filesystem

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.actions.trash.TrashRestorer
import app.osprey.actions.trash.TrashScanner
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val NAME = "IMG_001.jpg"
private const val SUFFIXED = "IMG_001_VisualDupes.jpg"
private val DEDUPE_BYTES = "dedupe photo bytes".toByteArray()
private val VISUAL_BYTES = "visual dupes photo bytes".toByteArray()

/**
 * A Trash restore of two same-name files (#85) on a real Storage Access Framework tree: the second file is moved
 * with a new name by the document provider, and both come back with the bytes they had in the Trash.
 */
@RunWith(AndroidJUnit4::class)
class TrashRestoreSafInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fs = AndroidFileSystemRepository(context)
    private var testRoot: DocumentFile? = null
    private lateinit var library: DocumentFile

    @Before
    fun setUp() {
        testRoot = SafGrant.workFolder(context, "osprey_trash_restore")
        assumeTrue(SafGrant.SKIP_REASON, testRoot != null)
        library = testRoot!!.createDirectory("Library") ?: error("could not create Library")
        val year = (library.createDirectory("Trash") ?: error("could not create Trash")).createDirectory("2021")
        seed(year?.createDirectory("Dedupe"), DEDUPE_BYTES)
        seed(year?.createDirectory("VisualDupes"), VISUAL_BYTES)
    }

    @After
    fun tearDown() {
        testRoot?.delete()
    }

    private fun seed(parent: DocumentFile?, bytes: ByteArray) {
        val doc = checkNotNull(parent).createFile("image/jpeg", NAME) ?: error("could not create $NAME")
        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) } ?: error("could not write $NAME")
    }

    private fun bytesOf(file: DocumentFile?): ByteArray =
        context.contentResolver.openInputStream(checkNotNull(file).uri)?.use { it.readBytes() } ?: error("cannot read")

    @Test
    fun twoSameNameFilesComeBackTogetherTheSecondSuffixedWithTheirBytes() {
        val root = AndroidFileHandle(library)
        val restorer = TrashRestorer(fs)

        val result = restorer.restore(root, restorer.plan(root, TrashScanner(fs).scan(root).entries))

        assertEquals(2, result.restored)
        val year = library.findFile("2021")
        assertEquals(setOf(NAME, SUFFIXED), year?.listFiles()?.mapNotNull { it.name }?.toSet())
        assertArrayEquals(DEDUPE_BYTES, bytesOf(year?.findFile(NAME)))
        assertArrayEquals(VISUAL_BYTES, bytesOf(year?.findFile(SUFFIXED)))
    }
}
