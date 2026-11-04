package app.osprey.desktop

import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.fixtures.Photos
import app.osprey.history.FileResult
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
 * Real JPEG and HEIC photos from the private fixtures through the real controllers, the real metadata reader and a
 * real history on disk (#79). The fixtures carry no dates, so the date of each copy comes from its name; what is real
 * is the bytes: an action that changes a name or a date must leave them as they were, and the sizes in the History
 * must be the files' own.
 */
class ActionHistoryRealPhotosTest {

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

    /** The fixture [folder]/[name] copied into the year as [asName]. */
    private fun copy(folder: String, name: String, asName: String): Path =
        Files.copy(photos.resolve(folder).resolve(name), harness.year.resolve(asName))

    private fun applyRecords(action: String) = harness.runsAfterRestart().single { it.action == action }.files

    @Test
    fun `Dates fixes the modified date of a real jpeg and a real heic and leaves their bytes alone`() {
        val jpeg = copy("jpg", "gull_1.jpg", "IMG_20170425_152602.jpg")
        val heic = copy("heic", "gull_1.heic", "IMG_20170426_101010.heic")
        val bytes = listOf(jpeg, heic).associateWith { sha256(it) }
        val sizes = listOf(jpeg, heic).associateWith { Files.size(it) }
        val launch = harness.launch()

        launch.scanAndApply(datesController(harness.fs, reader, launch.jobs))

        val expected = mapOf(
            jpeg.fileName.toString() to LocalDateTime.of(2017, 4, 25, 15, 26, 2),
            heic.fileName.toString() to LocalDateTime.of(2017, 4, 26, 10, 10, 10),
        ).mapValues { it.value.atZone(ZoneId.systemDefault()).toInstant() }
        val records = applyRecords("Dates 2021 apply").associateBy { it.path.substringAfterLast('/') }
        assertEquals(expected.keys, records.keys)
        for ((name, instant) in expected) {
            val record = records.getValue(name)
            val path = harness.year.resolve(name)
            assertEquals(FileResult.Done, record.result)
            assertEquals(instant, record.after?.modified)
            assertEquals(instant.toEpochMilli(), Files.getLastModifiedTime(path).toMillis())
            assertEquals(sizes.getValue(path), record.before?.size)
            assertEquals(sizes.getValue(path), record.after?.size)
            assertEquals(bytes.getValue(path), sha256(path))
        }
    }

    @Test
    fun `Standardize renames a real jpeg and a real heic from their names and leaves their bytes alone`() {
        val jpeg = copy("jpg", "duck_1.jpg", "download_20160828_193034.jpg")
        val heic = copy("heic", "duck_1.heic", "download_20160829_101010.heic")
        val bytes = mapOf(
            "IMG_2016-08-28_19-30-34.jpg" to sha256(jpeg),
            "IMG_2016-08-29_10-10-10.heic" to sha256(heic),
        )
        val launch = harness.launch()

        launch.scanAndApply(standardizeController(harness.fs, reader, launch.jobs))

        val names = applyRecords("Standardize 2021 apply").associate { it.before?.name to it.after?.name }
        assertEquals(
            mapOf(
                "download_20160828_193034.jpg" to "IMG_2016-08-28_19-30-34.jpg",
                "download_20160829_101010.heic" to "IMG_2016-08-29_10-10-10.heic",
            ),
            names,
        )
        assertEquals(bytes, bytes.keys.associateWith { sha256(harness.year.resolve(it)) })
        assertTrue(Files.notExists(harness.year.resolve("download_20160828_193034.jpg")))
    }

    @Test
    fun `Rename strips the suffix of a real jpeg and a real heic and leaves their bytes alone`() {
        val jpeg = copy("jpg", "olive_1.jpg", "olive (1).jpg")
        val heic = copy("heic", "olive_1.heic", "olive (1).heic")
        val bytes = listOf(sha256(jpeg), sha256(heic))
        val sizes = listOf(Files.size(jpeg), Files.size(heic))
        val launch = harness.launch()

        launch.scanAndApply(renameController(harness.fs, launch.jobs))

        val records = applyRecords("Rename 2021 apply").sortedBy { it.after?.name }
        assertEquals(listOf("olive.heic", "olive.jpg"), records.map { it.after?.name })
        assertEquals(listOf("olive (1).heic", "olive (1).jpg"), records.map { it.before?.name })
        assertEquals(sizes.sorted(), records.map { it.after?.size }.sortedBy { it })
        assertEquals(bytes.sorted(), listOf("olive.jpg", "olive.heic").map { sha256(harness.year.resolve(it)) }.sorted())
    }
}
