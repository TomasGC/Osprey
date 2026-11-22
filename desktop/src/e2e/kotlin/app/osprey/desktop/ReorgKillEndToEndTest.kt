package app.osprey.desktop

import app.osprey.actions.reorg.DuplicateChoice
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.jobs.NoJobControl
import app.osprey.settings.InputMode
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val PHOTO = "IMG_20180101_000000.jpg"

/**
 * A real process killed between the verified copy and the delete of a Reorg Move (#91): the original is left in the
 * input and its copy is in the library. The next launch must offer the leftover as a pair, and setting the input file
 * aside must leave the input folder clean and the library copy untouched.
 */
class ReorgKillEndToEndTest : TimedTest() {

    private lateinit var root: Path
    private val library get() = root.resolve("library")
    private val camera get() = root.resolve("Camera")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("osprey-reorg-kill-e2e")
        Files.createDirectories(library)
        Files.createDirectories(camera)
        Files.writeString(camera.resolve(PHOTO), "photo bytes")
    }

    @After
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    /** Starts the app process and returns once it is inside the delete. */
    private fun startAndWaitUntilMidMove(): Process = startUntilReady(
        appCommand("app.osprey.desktop.KilledMidMoveKt", library.toString(), camera.toString()),
        "mid-move",
    )

    @Test
    fun `a Move killed after its verified copy leaves a pair the next launch offers and can settle`() {
        val process = startAndWaitUntilMidMove()

        process.destroyForcibly().waitFor()

        assertEquals("photo bytes", Files.readString(camera.resolve(PHOTO)))
        assertEquals("photo bytes", Files.readString(library.resolve("2018").resolve(PHOTO)))
        val planner = ReorgPlanner(DesktopFileSystemRepository(), DesktopMediaMetadataReader())
        val output = PathHandle(library)
        val sources = listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE))
        val plan = planner.plan(output, sources)
        assertEquals(emptyList<Any>(), plan.items)
        val pair = plan.duplicates.single()

        val result = planner.apply(
            plan,
            output,
            listOf(DuplicateChoice(pair, keepInput = false, keepLibrary = true)),
            NoJobControl,
        )

        assertEquals(1, result.trashed)
        assertEquals(emptyList<Any>(), result.failures)
        assertFalse(Files.exists(camera.resolve(PHOTO)))
        assertEquals("photo bytes", Files.readString(library.resolve("2018").resolve(PHOTO)))
        assertEquals("photo bytes", Files.readString(library.resolve("Trash/2018/Reorg").resolve(PHOTO)))
        val again = planner.plan(output, sources)
        assertEquals(emptyList<Any>(), again.items + again.duplicates)
    }
}
