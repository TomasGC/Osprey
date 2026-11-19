package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.trash.TrashLocation
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.desktop.media.uprightDesktopMediaMetadataReader
import app.osprey.desktop.testing.TimedTest
import app.osprey.fixtures.Photos
import app.osprey.history.FileResult
import app.osprey.history.RetryPlanner
import app.osprey.history.RunOrigin
import app.osprey.history.RunRecord
import app.osprey.history.RunState
import app.osprey.settings.InputMode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Retry failed (#83) on real JPEG and HEIC photos from the private fixtures: a flatten that cannot move one of two
 * photos is retried by a new launch, and both photos end up in the year with the bytes they started with.
 */
class RetryRealPhotosTest : TimedTest() {

    private lateinit var harness: ActionHarness
    private lateinit var photos: Path
    private val reader = DesktopMediaMetadataReader()

    @Before
    fun setUp() {
        photos = Photos.need()
        harness = ActionHarness()
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) harness.delete()
    }

    private fun sha256(path: Path): String =
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private fun failedPaths(run: RunRecord) = run.files.filter { it.result == FileResult.Failed }.map { it.path }

    @Test
    fun `a flatten that could not move a real heic moves it on retry and leaves both photos' bytes alone`() {
        val folder = Files.createDirectories(harness.year.resolve("sub"))
        val jpeg = Files.copy(photos.resolve("jpg").resolve("gull_1.jpg"), folder.resolve("gull.jpg"))
        val heic = Files.copy(photos.resolve("heic").resolve("gull_1.heic"), folder.resolve("gull.heic"))
        val bytes = mapOf("gull.jpg" to sha256(jpeg), "gull.heic" to sha256(heic))
        val first = harness.launch()
        first.scanAndApply(flattenController(FailsToMove(harness.fs, "gull.heic"), first.jobs) { harness.libraryLabel })
        val run = harness.runsAfterRestart().single { it.action == "Flatten 2021 apply" }
        val second = harness.launch()

        flattenController(harness.fs, second.jobs) { harness.libraryLabel }.onRetry(harness.yearHandle(), run, "2021")
        second.awaitJobs()

        assertEquals(RunState.Failed, run.state)
        assertEquals(listOf("2021/sub/gull.heic"), run.files.filter { it.result == FileResult.Failed }.map { it.path })
        assertTrue(RetryPlanner.canRetry(run, harness.libraryLabel, "2021"))
        assertEquals(bytes, bytes.keys.associateWith { sha256(harness.year.resolve(it)) })
        assertTrue(Files.notExists(harness.year.resolve("sub")))
        val retry = harness.runsAfterRestart().single { it.action == "Flatten 2021 retry" }
        assertEquals(RunOrigin("flatten", harness.libraryLabel, "2021", retryOf = run.id), retry.origin)
        assertEquals(RunState.Done, retry.state)
        assertEquals(listOf("2021/sub/gull.heic"), retry.files.map { it.path })
    }

    @Test
    fun `a retry leaves a real photo that was not part of the failed run where it is`() {
        val folder = Files.createDirectories(harness.year.resolve("sub"))
        Files.copy(photos.resolve("jpg").resolve("gull_1.jpg"), folder.resolve("gull.jpg"))
        Files.copy(photos.resolve("heic").resolve("gull_1.heic"), folder.resolve("gull.heic"))
        val first = harness.launch()
        first.scanAndApply(flattenController(FailsToMove(harness.fs, "gull.heic"), first.jobs) { harness.libraryLabel })
        val run = harness.runsAfterRestart().single { it.action == "Flatten 2021 apply" }
        val later = Files.copy(photos.resolve("jpg").resolve("duck_1.jpg"), folder.resolve("duck.jpg"))
        val laterBytes = sha256(later)
        val second = harness.launch()

        flattenController(harness.fs, second.jobs) { harness.libraryLabel }.onRetry(harness.yearHandle(), run, "2021")
        second.awaitJobs()

        assertTrue(Files.exists(harness.year.resolve("gull.heic")))
        assertTrue(Files.exists(folder.resolve("duck.jpg")))
        assertEquals(laterBytes, sha256(folder.resolve("duck.jpg")))
        val retry = harness.runsAfterRestart().single { it.action == "Flatten 2021 retry" }
        assertEquals(listOf("2021/sub/gull.heic"), retry.files.map { it.path })
    }

    @Test
    fun `a dates run that could not stamp a real heic stamps it on retry and leaves both photos' bytes alone`() {
        val jpeg = Files.copy(
            photos.resolve("jpg").resolve("gull_1.jpg"),
            harness.year.resolve("IMG_20170425_152602.jpg"),
        )
        val heic = Files.copy(
            photos.resolve("heic").resolve("gull_1.heic"),
            harness.year.resolve("IMG_20170426_101010.heic"),
        )
        val bytes = listOf(jpeg, heic).associateWith { sha256(it) }
        val first = harness.launch()
        val failing = FailsToStamp(harness.fs, heic.fileName.toString())
        first.scanAndApply(datesController(failing, reader, first.jobs) { harness.libraryLabel })
        val run = harness.runsAfterRestart().single { it.action == "Dates 2021 apply" }
        val jpegStamp = Files.getLastModifiedTime(jpeg).toMillis()
        val second = harness.launch()

        datesController(harness.fs, reader, second.jobs) { harness.libraryLabel }.onRetry(
            harness.yearHandle(),
            run,
            "2021",
        )
        second.awaitJobs()

        val expected =
            LocalDateTime.of(2017, 4, 26, 10, 10, 10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(RunState.Failed, run.state)
        assertEquals(expected, Files.getLastModifiedTime(heic).toMillis())
        assertEquals(jpegStamp, Files.getLastModifiedTime(jpeg).toMillis())
        assertEquals(bytes, bytes.keys.associateWith { sha256(it) })
        val retry = harness.runsAfterRestart().single { it.action == "Dates 2021 retry" }
        assertEquals(RunOrigin("dates", harness.libraryLabel, "2021", retryOf = run.id), retry.origin)
        assertEquals(failedPaths(run), retry.files.map { it.path })
        assertEquals(RunState.Done, retry.state)
    }

    @Test
    fun `a standardize that could not rename a real heic renames it on retry and leaves both photos' bytes alone`() {
        val jpeg = Files.copy(
            photos.resolve("jpg").resolve("duck_1.jpg"),
            harness.year.resolve("download_20160828_193034.jpg"),
        )
        val heic = Files.copy(
            photos.resolve("heic").resolve("duck_1.heic"),
            harness.year.resolve("download_20160829_101010.heic"),
        )
        val bytes = mapOf("IMG_2016-08-28_19-30-34.jpg" to sha256(jpeg), "IMG_2016-08-29_10-10-10.heic" to sha256(heic))
        val first = harness.launch()
        val failing = FailsToRename(harness.fs, heic.fileName.toString())
        first.scanAndApply(standardizeController(failing, reader, first.jobs) { harness.libraryLabel })
        val run = harness.runsAfterRestart().single { it.action == "Standardize 2021 apply" }
        val second = harness.launch()

        standardizeController(harness.fs, reader, second.jobs) { harness.libraryLabel }
            .onRetry(harness.yearHandle(), run, "2021")
        second.awaitJobs()

        assertEquals(bytes, bytes.keys.associateWith { sha256(harness.year.resolve(it)) })
        assertTrue(Files.notExists(heic))
        val retry = harness.runsAfterRestart().single { it.action == "Standardize 2021 retry" }
        assertEquals(RunOrigin("standardize", harness.libraryLabel, "2021", retryOf = run.id), retry.origin)
        assertEquals(failedPaths(run), retry.files.map { it.path })
    }

    @Test
    fun `a reorg that could not copy a real heic files it on retry and leaves both photos' bytes alone`() {
        val camera = Files.createDirectory(harness.root.resolve("Camera"))
        val jpeg = Files.copy(photos.resolve("jpg").resolve("gull_1.jpg"), camera.resolve("IMG_20170425_152602.jpg"))
        val heic = Files.copy(photos.resolve("heic").resolve("gull_1.heic"), camera.resolve("IMG_20170426_101010.heic"))
        val bytes = mapOf("IMG_20170425_152602.jpg" to sha256(jpeg), "IMG_20170426_101010.heic" to sha256(heic))
        val sources = listOf(ReorgSource(PathHandle(camera), "Camera", InputMode.MOVE))
        val output = PathHandle(harness.library)
        val first = harness.launch()
        val failing = ReorgPlanner(FailsToCopy(harness.fs, heic.fileName.toString()), reader)
        val firstReorg = ReorgController(failing, first.jobs, { sources }, { harness.libraryLabel })
        firstReorg.onScan(output)
        first.awaitJobs()
        firstReorg.onApply(output)
        first.awaitJobs()
        val run = harness.runsAfterRestart().single { it.action == "Reorg apply" }
        val second = harness.launch()

        ReorgController(ReorgPlanner(harness.fs, reader), second.jobs, { sources }, { harness.libraryLabel })
            .onRetry(output, run)
        second.awaitJobs()

        assertEquals(bytes, bytes.keys.associateWith { sha256(harness.library.resolve("2017").resolve(it)) })
        assertTrue(Files.notExists(heic))
        val retry = harness.runsAfterRestart().single { it.action == "Reorg retry" }
        assertEquals(RunOrigin("reorg", harness.libraryLabel, retryOf = run.id), retry.origin)
        assertEquals(failedPaths(run), retry.files.map { it.path })
    }

    @Test
    fun `a dedupe run that could not trash a copy of a real heic trashes it on retry and keeps the originals' bytes`() {
        val originals = Photos.heic(photos).take(2).map { Files.copy(it, harness.year.resolve(it.fileName)) }
        val copies = originals.map {
            Files.copy(it, harness.year.resolve(it.fileName.toString().replace(".heic", "_copy.heic")))
        }
        val bytes = originals.associateWith { sha256(it) }
        val trash = { TrashLocation(PathHandle(harness.library), "2021") }
        val first = harness.launch()
        val failing = FailsToMove(harness.fs, copies.last().fileName.toString())
        first.scanAndApply(dedupeController(failing, first.jobs, trash) { harness.libraryLabel })
        val run = harness.runsAfterRestart().single { it.action == "Dedupe 2021 apply" }
        val second = harness.launch()

        dedupeController(harness.fs, second.jobs, trash) { harness.libraryLabel }
            .onRetry(harness.yearHandle(), run, "2021")
        second.awaitJobs()

        val trashed = harness.library.resolve("Trash/2021/Dedupe")
        val inTrash = Files.list(trashed).use { l -> l.map { it.fileName.toString() }.sorted().toList() }
        assertEquals(copies.map { it.fileName.toString() }.sorted(), inTrash)
        assertEquals(bytes, bytes.keys.associateWith { sha256(it) })
        copies.forEach { assertTrue(Files.notExists(it)) }
        assertEquals(listOf("2021/${copies.last().fileName}"), failedPaths(run))
        val retry = harness.runsAfterRestart().single { it.action == "Dedupe 2021 retry" }
        assertEquals(RunOrigin("dedupe", harness.libraryLabel, "2021", retryOf = run.id), retry.origin)
        assertEquals(failedPaths(run), retry.files.map { it.path })
    }

    @Test
    fun `a visual dupes move that could not trash a real copy trashes it on retry and keeps the ticked photo`() {
        val subject = Photos.png(photos).first().fileName.toString().removeSuffix(".png")
        val capture = listOf(Photos.heic(photos), Photos.png(photos), Photos.jpeg(photos))
            .map { list -> list.first { it.fileName.toString().substringBeforeLast('.') == subject } }
            .map { Files.copy(it, harness.year.resolve(it.fileName)) }
        val kept = capture.first { it.fileName.toString().endsWith(".heic") }
        val keptBytes = sha256(kept)
        val trash = { TrashLocation(PathHandle(harness.library), "2021") }
        val upright = uprightDesktopMediaMetadataReader()
        val first = harness.launch()
        val failing = VisualDupesController(
            FailsToMove(harness.fs, "$subject.png"), upright, first.jobs, { harness.libraryLabel }, trash,
        )
        failing.onScan(harness.yearHandle(), "2021")
        first.awaitJobs()
        val group = failing.groupsIn(harness.yearHandle()).single()
        capture.forEach { group.keep[it.fileName.toString()] = it == kept }
        failing.moveUnchecked(harness.yearHandle(), group)
        first.awaitJobs()
        val run = harness.runsAfterRestart().single { it.action.startsWith("Visual Dupes 2021 group") }
        val second = harness.launch()

        VisualDupesController(harness.fs, upright, second.jobs, { harness.libraryLabel }, trash)
            .onRetry(harness.yearHandle(), run, "2021")
        second.awaitJobs()

        val trashed = harness.library.resolve("Trash/2021/VisualDupes")
        val inTrash = Files.list(trashed).use { l -> l.map { it.fileName.toString() }.sorted().toList() }
        assertEquals(listOf("$subject.jpg", "$subject.png"), inTrash)
        assertEquals(keptBytes, sha256(kept))
        assertEquals(listOf("2021/$subject.png"), failedPaths(run))
        val retry = harness.runsAfterRestart().single { it.action == "Visual Dupes 2021 retry" }
        assertEquals(RunOrigin("visual-dupes", harness.libraryLabel, "2021", retryOf = run.id), retry.origin)
        assertEquals(failedPaths(run), retry.files.map { it.path })
    }
}
