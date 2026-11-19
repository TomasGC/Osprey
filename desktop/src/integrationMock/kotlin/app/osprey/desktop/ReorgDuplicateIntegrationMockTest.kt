package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.FileResult
import app.osprey.history.RunState
import app.osprey.settings.InputMode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

private const val LIBRARY_FILE = "IMG_20180101_000000.jpg"
private const val INPUT_FILE = "IMG_20180101_000001.jpg"

/**
  * The "Which files do you want to keep?" flow of Reorg (#91) through its real controller, real job queue, real files
  * and
 * a real history on disk, read back the way a new launch would.
 */
class ReorgDuplicateIntegrationMockTest : TimedTest() {

    private val harness = ActionHarness()
    private val camera: Path = Files.createDirectories(harness.root.resolve("Camera"))
    private val output = PathHandle(harness.library)

    @After
    fun tearDown() = harness.delete()

    private fun libraryFile(folder: String, name: String, content: String) {
        Files.writeString(Files.createDirectories(harness.library.resolve(folder)).resolve(name), content)
    }

    private fun inputFile(name: String, content: String) = Files.writeString(camera.resolve(name), content)

    private fun controller(launch: ActionHarness.Launch, fs: FileSystemRepository = harness.fs) = ReorgController(
        ReorgPlanner(fs, harness.reader), launch.jobs,
        { listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE)) },
    )

    /** Scans, asks, and returns the controller with its review open on the pairs found. */
    private fun ActionHarness.Launch.review(controller: ReorgController): ReorgController {
        controller.onScan(output)
        awaitJobs()
        controller.onApply(output)
        return controller
    }

    private fun ActionHarness.Launch.continueWith(controller: ReorgController) {
        controller.onContinue(output)
        awaitJobs()
    }

    private fun applyRun() = harness.runsAfterRestart().single { it.action == "Reorg apply" }

    @Test
    fun `continuing with the input file unticked is recorded in the history as a file set aside`() {
        libraryFile("2018", LIBRARY_FILE, "a")
        inputFile(INPUT_FILE, "a")
        val launch = harness.launch()
        val reorg = launch.review(controller(launch))
        reorg.review.toggleInput(0)

        launch.continueWith(reorg)

        val run = applyRun()
        assertEquals(RunState.Done, run.state)
        assertEquals(
            listOf("Camera/$INPUT_FILE" to "moved to Trash/2018/Reorg/$INPUT_FILE"),
            run.files.map { it.path to it.message },
        )
        assertEquals(FileResult.Done, run.files.single().result)
        assertEquals(emptyList<String>(), Files.list(camera).use { l -> l.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `a file that cannot be set aside is a failed file and the next pair is still handled`() {
        libraryFile("2018", LIBRARY_FILE, "one")
        libraryFile("2018", "IMG_20180102_000000.jpg", "two")
        inputFile(INPUT_FILE, "one")
        inputFile("IMG_20180102_000001.jpg", "two")
        val launch = harness.launch()
        val reorg = launch.review(controller(launch, FailsToRemove(harness.fs, INPUT_FILE)))
        reorg.review.toggleInputColumn()

        launch.continueWith(reorg)

        val run = applyRun()
        assertEquals(RunState.Failed, run.state)
        val byPath = run.files.associateBy { it.path }
        assertEquals(FileResult.Failed, byPath.getValue("Camera/$INPUT_FILE").result)
        assertEquals("locked", byPath.getValue("Camera/$INPUT_FILE").message)
        assertEquals(FileResult.Done, byPath.getValue("Camera/IMG_20180102_000001.jpg").result)
    }

    @Test
    fun `a library file set aside before its refile says the refile was dropped`() {
        libraryFile("2019", LIBRARY_FILE, "a")
        inputFile(INPUT_FILE, "a")
        val launch = harness.launch()
        val reorg = launch.review(controller(launch))
        reorg.review.toggleLibrary(0)

        launch.continueWith(reorg)

        val record = applyRun().files.single()
        assertEquals("2019/$LIBRARY_FILE", record.path)
        assertEquals("moved to Trash/2019/Reorg/$LIBRARY_FILE (its refile was dropped)", record.message)
    }

    @Test
    fun `cancelling the review writes no run and leaves every file`() {
        libraryFile("2018", LIBRARY_FILE, "a")
        inputFile(INPUT_FILE, "a")
        val launch = harness.launch()
        val reorg = launch.review(controller(launch))

        reorg.onCancel()

        assertEquals(emptyList<String>(), harness.runsAfterRestart().map { it.action }.filter { it == "Reorg apply" })
        assertEquals(true, Files.exists(camera.resolve(INPUT_FILE)))
        assertEquals(true, Files.exists(harness.library.resolve("2018").resolve(LIBRARY_FILE)))
    }
}
