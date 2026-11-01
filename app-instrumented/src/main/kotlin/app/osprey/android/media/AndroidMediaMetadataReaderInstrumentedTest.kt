package app.osprey.android.media

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.android.filesystem.SafGrant
import app.osprey.media.MediaDateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

private const val IMAGE_SIZE = 64

/**
 * [AndroidMediaMetadataReader] against a real on-device JPEG with real EXIF, written through a
 * real SAF file descriptor -- the tree grant comes from [SafGrant], as in
 * [app.osprey.android.filesystem.AndroidFileSystemRepositoryInstrumentedTest].
 */
@RunWith(AndroidJUnit4::class)
class AndroidMediaMetadataReaderInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val reader = AndroidMediaMetadataReader(context)
    private var testRoot: DocumentFile? = null

    @Before
    fun setUp() {
        testRoot = SafGrant.workFolder(context, "osprey_media_instrumented_test")
        assumeTrue(SafGrant.SKIP_REASON, testRoot != null)
    }

    @After
    fun tearDown() {
        testRoot?.delete()
    }

    @Test
    fun readsRealExifAndDecodesARealImage() {
        val doc = testRoot!!.createFile("image/jpeg", "photo.jpg") ?: error("could not create photo.jpg")
        writeJpegWithExifDatetimeOriginal(doc, "2019:09:07 23:22:34")

        val date = reader.captureDateTime(AndroidFileHandle(doc))
        assertNotNull(date)
        assertEquals(MediaDateSource.EXIF, date!!.source)
        assertEquals(2019, date.dateTime.year)
        assertEquals(9, date.dateTime.monthValue)
        assertEquals(7, date.dateTime.dayOfMonth)
        assertEquals(23, date.dateTime.hour)
        assertEquals(22, date.dateTime.minute)
        assertEquals(34, date.dateTime.second)

        val frame = reader.decodeFrame(AndroidFileHandle(doc))
        assertNotNull(frame)
        assertEquals(IMAGE_SIZE, frame!!.width)
        assertEquals(IMAGE_SIZE, frame.height)
    }

    private fun writeJpegWithExifDatetimeOriginal(doc: DocumentFile, exifDateTime: String) {
        val bitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888)
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        } ?: error("could not open ${doc.name} for write")

        context.contentResolver.openFileDescriptor(doc.uri, "rw")?.use { pfd: ParcelFileDescriptor ->
            val exif = ExifInterface(pfd.fileDescriptor)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDateTime)
            exif.saveAttributes()
        } ?: error("could not open ${doc.name} for EXIF write")
    }
}
