package app.osprey.android.media

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.media.ExifOrientation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val WIDE = 64
private const val HIGH = 32
private const val JPEG_QUALITY = 90

/**
 * [AndroidOrientationSource] against real on-device JPEGs with a real EXIF Orientation tag (#51). Same persisted-grant
 * reuse and skip-if-none as [AndroidMediaMetadataReaderInstrumentedTest]. The HEIC rotation property cannot be
 * generated on a device, and real HEIC photos carry GPS data, so it is checked by hand against a real photo.
 */
@RunWith(AndroidJUnit4::class)
class AndroidOrientationSourceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = AndroidOrientationSource(context)
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
        testRoot = root!!.createDirectory("osprey_orientation_instrumented_test_${System.currentTimeMillis()}")
            ?: error("could not create a throwaway test folder under the granted tree")
    }

    @After
    fun tearDown() {
        testRoot?.delete()
    }

    private fun jpeg(name: String, orientation: Int?): DocumentFile {
        val doc = testRoot!!.createFile("image/jpeg", name) ?: error("could not create $name")
        val bitmap = Bitmap.createBitmap(WIDE, HIGH, Bitmap.Config.ARGB_8888)
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        } ?: error("could not open $name for write")
        if (orientation != null) {
            context.contentResolver.openFileDescriptor(doc.uri, "rw")?.use { pfd: ParcelFileDescriptor ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                exif.saveAttributes()
            } ?: error("could not open $name for EXIF write")
        }
        return doc
    }

    @Test
    fun aJpegReportsItsStoredOrientation() {
        assertEquals(ExifOrientation.ROTATE_90, source.orientationOf(AndroidFileHandle(jpeg("sideways.jpg", 6))))
        assertEquals(ExifOrientation.ROTATE_270, source.orientationOf(AndroidFileHandle(jpeg("other.jpg", 8))))
    }

    @Test
    fun aJpegWithoutOrientationIsUpright() {
        assertEquals(ExifOrientation.NORMAL, source.orientationOf(AndroidFileHandle(jpeg("plain.jpg", null))))
    }

    @Test
    fun aVideoIsUpright() {
        val doc = testRoot!!.createFile("video/mp4", "clip.mp4") ?: error("could not create clip.mp4")

        assertEquals(ExifOrientation.NORMAL, source.orientationOf(AndroidFileHandle(doc)))
    }

    @Test
    fun theAppReaderDecodesASidewaysJpegUpright() {
        val frame = uprightAndroidMediaMetadataReader(context)
            .decodeFrame(AndroidFileHandle(jpeg("sideways.jpg", 6)))!!

        assertEquals(HIGH, frame.width)
        assertEquals(WIDE, frame.height)
    }
}
