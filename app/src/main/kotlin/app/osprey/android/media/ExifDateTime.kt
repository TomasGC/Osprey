package app.osprey.android.media

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val EXIF_DATETIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

/**
 * EXIF capture datetime for [uri], via `androidx.exifinterface` (works directly against a SAF
 * stream, no raw-path requirement -- the right fit here, unlike desktop's file-based
 * `metadata-extractor`). Same priority as the POC's `exif_datetime` and desktop's
 * [app.osprey.desktop.media.readExifDateTime]: DateTimeOriginal, then DateTimeDigitized, then the
 * top-level DateTime tag. Returns null if [uri] isn't a readable image or carries no such tag,
 * never throws.
 */
fun readExifDateTime(context: Context, uri: Uri): LocalDateTime? {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    return input.use { stream ->
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        val exif = try {
            ExifInterface(stream)
        } catch (e: Exception) {
            return@use null
        }
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        raw?.let(::parseExifDateTime)
    }
}

private fun parseExifDateTime(raw: String): LocalDateTime? = try {
    LocalDateTime.parse(raw, EXIF_DATETIME_FORMAT)
} catch (e: DateTimeParseException) {
    null
}
