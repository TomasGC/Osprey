package app.osprey.android.media

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.media.ExifOrientation
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val FIXTURES = "/data/local/tmp/osprey-fixtures/heic"
private const val REQUIRE_ARGUMENT = "requireFixtures"
private const val REQUIRED = "1"
private const val WITH_ROTATION_PROPERTY = "gull_1.heic" // stored landscape, irot 3: upright it is a portrait
private const val WITHOUT_ROTATION_PROPERTY = "sun_1.heic" // a landscape with no irot
private const val FRAME_SIDE = 256

/**
 * [AndroidOrientationSource] and the app's reader against real HEIC photos on a real device (#76), the one thing a
 * generated file cannot give: a HEVC picture with a real `irot` property. The photos are pushed by hand (or by CI) with
 * `adb push Osprey-fixtures/heic/. /data/local/tmp/osprey-fixtures/heic/`, so nothing private is in the APK; without
 * them the tests are skipped, or fail when the run is given `-e requireFixtures 1`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidRealHeicInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    /** The photo [name] copied into the app's cache folder, or the test is skipped (failed when they are required). */
    private fun photo(name: String): AndroidFileHandle {
        val source = File(FIXTURES, name)
        if (!source.isFile) {
            val reason = "The real photo $name is missing: push Osprey-fixtures/heic to $FIXTURES"
            if (InstrumentationRegistry.getArguments().getString(REQUIRE_ARGUMENT) == REQUIRED) fail(reason)
            assumeTrue(reason, false)
        }
        source.copyTo(File(context.cacheDir, name).also { files += it }, overwrite = true)
        // Found through its folder, like every photo the app lists, so the handle knows its parent.
        return AndroidFileHandle(DocumentFile.fromFile(context.cacheDir).findFile(name) ?: error("no $name"))
    }

    @Test
    fun aRealHeicWithARotationPropertyReportsIt() {
        assertEquals(ExifOrientation.ROTATE_90, AndroidOrientationSource(context).orientationOf(photo(WITH_ROTATION_PROPERTY)))
    }

    @Test
    fun aRealHeicWithoutARotationPropertyIsUpright() {
        assertEquals(ExifOrientation.NORMAL, AndroidOrientationSource(context).orientationOf(photo(WITHOUT_ROTATION_PROPERTY)))
    }

    @Test
    fun theAppReaderShowsRealHeicsUpright() {
        val reader = uprightAndroidMediaMetadataReader(context)

        val portrait = reader.decodeFrame(photo(WITH_ROTATION_PROPERTY), FRAME_SIDE)!!
        val landscape = reader.decodeFrame(photo(WITHOUT_ROTATION_PROPERTY), FRAME_SIDE)!!

        assertTrue("the turned photo is a portrait: ${portrait.width}x${portrait.height}", portrait.height > portrait.width)
        assertTrue("the plain photo is a landscape: ${landscape.width}x${landscape.height}", landscape.width > landscape.height)
    }
}
