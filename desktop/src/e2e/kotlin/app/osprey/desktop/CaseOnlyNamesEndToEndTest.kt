package app.osprey.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.desktop.testing.fileTree
import app.osprey.desktop.testing.ignoresCase
import app.osprey.desktop.ui.OspreyApp
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.history.FileResult
import app.osprey.history.openHistory
import app.osprey.jobs.JobState
import app.osprey.settings.InputMode
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val WAIT_MS = 30_000L

/**
 * Names that differ only by case through the whole desktop app and the real file system (#132). This tier runs on the
 * Windows runner as well as on Linux, so it is where CI sees a file system that takes two such names for one. The
 * actions must end with the same names on both; only what the repository itself refuses depends on the file system,
 * which is probed.
 */
@OptIn(ExperimentalTestApi::class)
class CaseOnlyNamesEndToEndTest : TimedTest() {

    private lateinit var root: Path
    private val library get() = root.resolve("library")
    private val year get() = library.resolve("2021")

    @Before
    fun setUp() {
        root = Files.createTempDirectory("osprey-case-e2e")
        Files.createDirectories(year)
    }

    @After
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String, content: String): Path {
        val path = library.resolve(relative)
        Files.createDirectories(path.parent)
        return Files.writeString(path, content)
    }

    private fun tree(): Set<String> = fileTree(library)

    private fun launch() = AppState(
        DesktopFileSystemRepository(),
        DesktopMediaMetadataReader(),
        FakeSettingsStore(),
        history = openHistory(root.resolve("history").toFile()),
    ).also { it.openLibrary(library) }

    private fun ComposeUiTest.waitForJobs(state: AppState) {
        waitForIdle()
        runBlocking {
            withTimeout(WAIT_MS) {
                state.jobs.entries.first { entries ->
                    entries.isNotEmpty() && entries.all { it.state is JobState.Finished }
                }
            }
        }
        waitForIdle()
    }

    /** Opens [action] in the sidebar, scans, applies, and checks the History lists no failed file. */
    private fun ComposeUiTest.runAction(action: String) {
        val state = launch()
        setContent { OspreyApp(state) }
        onNodeWithText(action).performClick()
        waitForIdle()
        onNodeWithText("Scan").performClick()
        waitForJobs(state)
        onNodeWithText("Apply").performClick()
        waitForJobs(state)
        val runs = openHistory(root.resolve("history").toFile()).runs()
        val failed = runs.flatMap { it.files }.filter { it.result == FileResult.Failed }
        assertEquals(emptyList<String>(), failed.map { "${it.path}: ${it.message}" })
    }

    @Test
    fun `Standardize keeps both names apart and renames a file whose own name differs only by case`() =
        runComposeUiTest {
            file("2021/20191231_235930.PNG", "one")
            file("2021/download_20191231_235930.png", "two")
            file("2021/img_2018-01-01_10-00-00.jpg", "three")

            runAction("Standardize")

            val files = tree()
            assertEquals(setOf("one", "two", "three"), files.map { it.substringAfter('=') }.toSet())
            val names = files.map { it.substringBefore('=') }
            assertEquals(
                setOf("2021/img_2019-12-31_23-59-30", "2021/img_2019-12-31_23-59-30__dup2"),
                names.filter { "2019" in it }.map { it.substringBeforeLast('.').lowercase() }.toSet(),
            )
            assertEquals(listOf("2021/IMG_2018-01-01_10-00-00.jpg"), names.filter { "2018" in it })
        }

    @Test
    fun `Flatten keeps every file`() = runComposeUiTest {
        file("2021/06/a.jpg", "one")
        file("2021/07/A.JPG", "two")
        file("2021/b.jpg", "three")
        file("2021/06/B.JPG", "four")

        runAction("Flatten")

        // Which subfolder is listed first is the file system's choice, so either of the two may keep the name.
        val kept = setOf("2021/b.jpg=three", "2021/B__flat1.JPG=four")
        val sixthFirst = kept + setOf("2021/a.jpg=one", "2021/A__flat1.JPG=two")
        val seventhFirst = kept + setOf("2021/A.JPG=two", "2021/a__flat1.jpg=one")
        val files = tree()
        assertTrue("$files", files == sixthFirst || files == seventhFirst)
    }

    @Test
    fun `Rename skips a clean name taken in another case`() = runComposeUiTest {
        file("2021/photo.JPG", "keep")
        file("2021/photo (1).jpg", "copy")

        runAction("Rename")

        assertEquals(setOf("2021/photo.JPG=keep", "2021/photo (1).jpg=copy"), tree())
    }

    @Test
    fun `Dedupe keeps what a lower case trash folder holds under another case`() = runComposeUiTest {
        file("trash/2021/Dedupe/BB.JPG", "old")
        file("2021/a.jpg", "same")
        file("2021/bb.jpg", "same")

        runAction("Dedupe")

        assertEquals(
            setOf("trash/2021/Dedupe/BB.JPG=old", "trash/2021/Dedupe/bb__dup1.jpg=same", "2021/a.jpg=same"),
            tree(),
        )
    }

    // Reorg through the planner: its screen needs saved settings, and its names are what is checked here.
    @Test
    fun `Reorg keeps both of two names`() {
        val camera = Files.createDirectory(root.resolve("Camera"))
        file("2019/IMG_20191231_235930.JPG", "library")
        Files.writeString(camera.resolve("IMG_20191231_235930.jpg"), "input")
        val planner = ReorgPlanner(DesktopFileSystemRepository(), DesktopMediaMetadataReader())
        val output = PathHandle(library)

        val plan = planner.plan(output, listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE)))
        val result = planner.apply(plan, output)

        assertEquals(emptyList<Any>(), result.failures)
        assertEquals(
            setOf("2019/IMG_20191231_235930.JPG=library", "2019/IMG_20191231_235930__year1.jpg=input"),
            tree(),
        )
    }

    @Test
    fun `the real file system never overwrites a file under a name of another case`() {
        val fs = DesktopFileSystemRepository()
        val a = PathHandle(file("2021/a.jpg", "one"))
        file("2021/b.jpg", "two")

        if (ignoresCase(root)) {
            assertThrows(FileAlreadyExistsException::class.java) { fs.rename(a, "B.JPG") }
            assertEquals(setOf("2021/a.jpg=one", "2021/b.jpg=two"), tree())
        } else {
            fs.rename(a, "B.JPG")
            assertEquals(setOf("2021/B.JPG=one", "2021/b.jpg=two"), tree())
        }
    }
}
