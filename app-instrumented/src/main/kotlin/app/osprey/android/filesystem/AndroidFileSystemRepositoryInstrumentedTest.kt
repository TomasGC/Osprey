package app.osprey.android.filesystem

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.filesystem.FileTimestamps
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs [AndroidFileSystemRepository] against a real Storage Access Framework tree on a real
 * device/emulator -- the DoD #13 asks for. SAF has no way to grant a tree permission without the
 * interactive system picker (confirmed by #9's spike too), so this reuses whatever tree this app
 * was already, manually, granted on this device: open the app once, tap "Pick folder", choose
 * any folder, tap Allow. The grant then persists across installs on this device/emulator, so this
 * test runs unattended from then on; with no persisted grant at all, it skips with a clear reason
 * rather than failing CI, which has no interactive picker to grant one.
 */
@RunWith(AndroidJUnit4::class)
class AndroidFileSystemRepositoryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fs = AndroidFileSystemRepository(context)
    private var testRoot: DocumentFile? = null

    @Before
    fun setUp() {
        val granted = context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission }
        assumeTrue(
            "No persisted SAF grant on this device -- open the app once, Pick folder, Allow, then re-run.",
            granted != null,
        )
        val root = DocumentFile.fromTreeUri(context, granted!!.uri)
        assumeTrue("Granted tree Uri no longer resolves to a real folder.", root != null && root.isDirectory)
        testRoot = root!!.createDirectory("osprey_instrumented_test_${System.currentTimeMillis()}")
            ?: error("could not create a throwaway test folder under the granted tree")
    }

    @After
    fun tearDown() {
        testRoot?.delete()
    }

    private fun seedFile(parent: DocumentFile, name: String, content: ByteArray): DocumentFile {
        val doc = parent.createFile("application/octet-stream", name) ?: error("could not create $name")
        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(content) } ?: error("could not open $name for write")
        return doc
    }

    @Test
    fun fullLifecycleAgainstARealSafTree() {
        val root = testRoot!!
        val rootHandle = AndroidFileHandle(root)
        seedFile(root, "a.bin", byteArrayOf(1, 2, 3))
        seedFile(root, "b.bin", byteArrayOf(4, 5, 6, 7))

        val listed = fs.list(rootHandle).associateBy { it.name }
        assertEquals(setOf("a.bin", "b.bin"), listed.keys)
        assertEquals(3L, listed.getValue("a.bin").sizeBytes)
        assertEquals(byteArrayOf(1, 2, 3).toList(), fs.readBytes(listed.getValue("a.bin").handle).toList())

        val renamed = fs.rename(listed.getValue("a.bin").handle, "a_renamed.bin")
        assertEquals(setOf("a_renamed.bin", "b.bin"), fs.list(rootHandle).map { it.name }.toSet())

        val subfolder = fs.createFolder(rootHandle, "sub")
        assertTrue(fs.list(rootHandle).any { it.name == "sub" && it.isDirectory })

        val movedHandle = fs.move(renamed, subfolder, "moved.bin")
        assertEquals(setOf("b.bin", "sub"), fs.list(rootHandle).map { it.name }.toSet())
        assertEquals(listOf("moved.bin"), fs.list(subfolder).map { it.name })
        assertEquals(byteArrayOf(1, 2, 3).toList(), fs.readBytes(movedHandle).toList())

        val beforeTimestamps = fs.readTimestamps(movedHandle)
        assertNull(beforeTimestamps.created)
        fs.writeTimestamps(movedHandle, FileTimestamps(modified = Instant.parse("2020-01-01T00:00:00Z")))
        // Confirmed no-op on scoped storage (#13): writing never throws, and never changes anything.
        assertEquals(beforeTimestamps, fs.readTimestamps(movedHandle))

        fs.deleteEmptyFolder(fs.createFolder(rootHandle, "empty"))
        assertEquals(setOf("b.bin", "sub"), fs.list(rootHandle).map { it.name }.toSet())
    }
}
