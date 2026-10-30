package app.osprey.fixtures

import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.AssumptionViolatedException

class PhotosTest {

    private lateinit var tmp: Path
    private val noEnv: (String) -> String? = { null }

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("osprey-photos-test")
    }

    @After
    fun tearDown() {
        tmp.toFile().deleteRecursively()
    }

    /** A repository root with its settings file, a `core` module folder inside it, and optionally the sibling. */
    private fun repo(withSibling: Boolean): Path {
        val root = Files.createDirectories(tmp.resolve("Osprey"))
        Files.writeString(root.resolve("settings.gradle.kts"), "")
        Files.createDirectories(root.resolve("core"))
        if (withSibling) Files.createDirectories(tmp.resolve("Osprey-fixtures"))
        return root.resolve("core")
    }

    @Test
    fun `the folder named by the variable wins`() {
        val named = Files.createDirectories(tmp.resolve("elsewhere"))
        val env = mapOf(Photos.FOLDER_VARIABLE to named.toString())

        assertEquals(named, Photos.locate(env::get, repo(withSibling = true)))
    }

    @Test
    fun `a variable pointing at nothing means no photos, never the sibling`() {
        val env = mapOf(Photos.FOLDER_VARIABLE to tmp.resolve("missing").toString())

        assertNull(Photos.locate(env::get, repo(withSibling = true)))
    }

    @Test
    fun `without a variable the sibling of the repository root is used, from any module folder`() {
        val module = repo(withSibling = true)

        assertEquals(tmp.resolve("Osprey-fixtures"), Photos.locate(noEnv, module))
        assertEquals(tmp.resolve("Osprey-fixtures"), Photos.locate(noEnv, module.parent))
    }

    @Test
    fun `no sibling and no variable means no photos`() {
        assertNull(Photos.locate(noEnv, repo(withSibling = false)))
    }

    @Test
    fun `outside a repository there is nothing to find`() {
        assertNull(Photos.locate(noEnv, Files.createDirectories(tmp.resolve("loose"))))
    }

    @Test
    fun `missing photos skip the test`() {
        assertThrows(AssumptionViolatedException::class.java) { Photos.need(noEnv, repo(withSibling = false)) }
    }

    @Test
    fun `missing photos fail the test where they are required`() {
        val env = mapOf(Photos.REQUIRE_VARIABLE to "1")

        val failure = assertThrows(AssertionError::class.java) { Photos.need(env::get, repo(withSibling = false)) }

        assertEquals(true, failure.message!!.contains(Photos.REQUIRE_VARIABLE))
    }

    @Test
    fun `any other value of the require variable still skips`() {
        val env = mapOf(Photos.REQUIRE_VARIABLE to "0")

        assertThrows(AssumptionViolatedException::class.java) { Photos.need(env::get, repo(withSibling = false)) }
    }

    @Test
    fun `present photos are handed over`() {
        assertEquals(tmp.resolve("Osprey-fixtures"), Photos.need(noEnv, repo(withSibling = true)))
    }

    @Test
    fun `heic lists only the heic files of the heic folder, in name order`() {
        val folder = Files.createDirectories(tmp.resolve("Osprey-fixtures"))
        val heic = Files.createDirectories(folder.resolve("heic"))
        listOf("b_1.heic", "a_1.heic", "c_1.HEIC", "note.txt").forEach { Files.writeString(heic.resolve(it), "x") }
        Files.createDirectories(folder.resolve("jpg"))
        Files.writeString(folder.resolve("jpg").resolve("z_1.heic"), "x")

        assertEquals(listOf("a_1.heic", "b_1.heic", "c_1.HEIC"), Photos.heic(folder).map { it.fileName.toString() })
    }

    @Test
    fun `jpeg and png list the files of their own folders, in name order`() {
        val folder = Files.createDirectories(tmp.resolve("Osprey-fixtures"))
        val jpg = Files.createDirectories(folder.resolve("jpg"))
        val png = Files.createDirectories(folder.resolve("png"))
        listOf("b_1.jpg", "a_1.jpeg", "c_1.JPG", "note.txt", "d_1.png").forEach { Files.writeString(jpg.resolve(it), "x") }
        listOf("b_1.png", "a_1.PNG", "z_1.jpg").forEach { Files.writeString(png.resolve(it), "x") }

        assertEquals(listOf("a_1.jpeg", "b_1.jpg", "c_1.JPG"), Photos.jpeg(folder).map { it.fileName.toString() })
        assertEquals(listOf("a_1.PNG", "b_1.png"), Photos.png(folder).map { it.fileName.toString() })
    }

    @Test
    fun `a folder with no jpg or png folder has no such photos`() {
        val folder = Files.createDirectories(tmp.resolve("Osprey-fixtures"))

        assertEquals(emptyList<Path>(), Photos.jpeg(folder))
        assertEquals(emptyList<Path>(), Photos.png(folder))
    }

    @Test
    fun `a folder with no heic folder has no heic photos`() {
        assertEquals(emptyList<Path>(), Photos.heic(Files.createDirectories(tmp.resolve("Osprey-fixtures"))))
    }
}
