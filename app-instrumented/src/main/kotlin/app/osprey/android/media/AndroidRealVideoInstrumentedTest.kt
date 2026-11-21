package app.osprey.android.media

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.actions.resolveMediaDate
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.utcToLocal
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val FIXTURES = "/data/local/tmp/osprey-fixtures/mp4"
private const val REQUIRE_ARGUMENT = "requireFixtures"
private const val REQUIRED = "1"
private const val CAMERA_VIDEO = "20190629_151754.mp4" // shot at UTC+2; its name is 17 s off the container
private const val WHATSAPP_VIDEO = "VID-20180314-WA0073.mp4" // no creation time
private const val RENAMED = "VID_2019-06-29_15-17-54.mp4" // what Standardize makes of the name's time

/**
 * The app's reader and the shared date resolution against real phone videos on a real device (#84): the container
 * time a phone writes (UTC), as this device reads it. The videos are pushed by hand (or by CI) with
 * `adb push Osprey-fixtures/mp4/. /data/local/tmp/osprey-fixtures/mp4/`, so nothing private is in the APK; without
 * them the tests are skipped, or fail when the run is given `-e requireFixtures 1`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidRealVideoInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val reader = uprightAndroidMediaMetadataReader(context)
    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    /** The video [name] copied into the app's cache folder as [asName], or the test is skipped (failed if required). */
    private fun video(name: String, asName: String = name): AndroidFileHandle {
        val source = File(FIXTURES, name)
        if (!source.isFile) {
            val reason = "The real video $name is missing: push Osprey-fixtures/mp4 to $FIXTURES"
            if (InstrumentationRegistry.getArguments().getString(REQUIRE_ARGUMENT) == REQUIRED) fail(reason)
            assumeTrue(reason, false)
        }
        source.copyTo(File(context.cacheDir, asName).also { files += it }, overwrite = true)
        return found(asName)
    }

    // Found through its folder, like every file the app lists, so the handle knows its parent.
    private fun found(name: String) =
        AndroidFileHandle(DocumentFile.fromFile(context.cacheDir).findFile(name) ?: error("no $name"))

    private fun resolved(handle: AndroidFileHandle, name: String): MediaDate? = resolveMediaDate(handle, name, reader)

    @Test
    fun theReaderGivesTheContainerTimeOfARealPhoneVideoInTheDeviceZone() {
        val date = reader.captureDateTime(video(CAMERA_VIDEO))

        assertEquals(utcToLocal(LocalDateTime.of(2019, 6, 29, 13, 18, 11)), date?.dateTime)
        assertEquals(MediaDateSource.VIDEO_METADATA, date?.source)
    }

    @Test
    fun aRealWhatsAppVideoHasNoContainerDate() {
        val date = reader.captureDateTime(video(WHATSAPP_VIDEO))

        assertTrue("a WhatsApp video carries no creation time: $date", date == null || date.dateTime.year < 1970)
    }

    @Test
    fun aRealCameraVideoIsDatedByItsNameWhereTheDeviceIsNotAtTheOffsetItWasShotAt() {
        val handle = video(CAMERA_VIDEO)

        val date = resolved(handle, CAMERA_VIDEO)

        // At UTC+2 the container already shows the camera's time within a few seconds; anywhere else the name wins.
        val shotZone = ZoneId.systemDefault().rules.getOffset(Instant.parse("2019-06-29T13:18:11Z")) ==
            ZoneOffset.ofHours(2)
        val expected = if (shotZone) {
            MediaDate(LocalDateTime.of(2019, 6, 29, 15, 18, 11), MediaDateSource.VIDEO_METADATA)
        } else {
            MediaDate(LocalDateTime.of(2019, 6, 29, 15, 17, 54), MediaDateSource.FILENAME)
        }
        assertEquals(expected, date)
    }

    @Test
    fun aRealCameraVideoKeepsItsTimeOnceStandardizeRenamedIt() {
        val handle = video(CAMERA_VIDEO)
        val before = resolved(handle, CAMERA_VIDEO)

        assertTrue("could not rename $CAMERA_VIDEO", handle.documentFile.renameTo(RENAMED))
        files += File(context.cacheDir, RENAMED)
        val after = resolved(found(RENAMED), RENAMED)

        assertEquals(before?.dateTime, after?.dateTime)
    }
}
