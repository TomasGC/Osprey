package app.osprey.android.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import app.osprey.actions.imageExtensions
import app.osprey.actions.splitStemAndSuffix
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.filesystem.FileHandle
import app.osprey.media.ExifOrientation
import app.osprey.media.OrientationSource

private const val HEIC_EXTENSION = ".heic"

/**
 * Orientation of a photo over the Storage Access Framework. A JPEG or PNG: the EXIF Orientation tag, through
 * `androidx.exifinterface` (it reads the PNG EXIF chunk too). A HEIC: its rotation property (`irot`), which the
 * platform decoder ignores and which the format makes authoritative over any EXIF copy, so only one of them is
 * ever applied. A video frame already comes out upright from the retriever.
 */
class AndroidOrientationSource(private val context: Context) : OrientationSource {

    override fun orientationOf(entry: FileHandle): ExifOrientation {
        val doc = (entry as AndroidFileHandle).documentFile
        val extension = splitStemAndSuffix(doc.name.orEmpty()).second.lowercase()
        return when {
            extension == HEIC_EXTENSION -> heicRotation(doc.uri)
            extension in imageExtensions -> exifOrientation(doc.uri)
            else -> ExifOrientation.NORMAL
        }
    }

    // An unreadable photo must never crash a scan: it is shown as stored.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun exifOrientation(uri: Uri): ExifOrientation = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifOrientation.fromExif(ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))
        } ?: ExifOrientation.NORMAL
    } catch (e: Exception) {
        ExifOrientation.NORMAL
    }

    // The retriever's rotation key, and HEIC decoding itself, start at Android 9 (API 28).
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun heicRotation(uri: Uri): ExifOrientation {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return ExifOrientation.NORMAL
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val degrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_ROTATION)
            ExifOrientation.fromClockwiseDegrees(degrees?.toIntOrNull())
        } catch (e: Exception) {
            ExifOrientation.NORMAL
        } finally {
            retriever.release()
        }
    }
}
