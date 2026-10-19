package app.osprey.desktop.media

import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.ImageProcessingException
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * EXIF capture datetime for [path], via `metadata-extractor` (more robust than Pillow's
 * `getexif()`, handles more manufacturer quirks) — same priority as the POC's `exif_datetime`:
 * DateTimeOriginal, then DateTimeDigitized, then the top-level DateTime tag. Returns null if
 * [path] isn't a readable image or carries no such tag, never throws.
 */
fun readExifDateTime(path: Path): LocalDateTime? {
    // Mirrors the POC's bare except: Exception -> None; malformed/unsupported image content can
    // throw unpredictable runtime exceptions deep inside the parser, and an unreadable file must
    // never crash the scan.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    val metadata = try {
        ImageMetadataReader.readMetadata(path.toFile())
    } catch (e: ImageProcessingException) {
        return null
    } catch (e: IOException) {
        return null
    } catch (e: Exception) {
        return null
    }

    val zone = TimeZone.getDefault()
    val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
    val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
    val date = subIfd?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, null, zone)
        ?: subIfd?.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED, null, zone)
        ?: ifd0?.getDate(ExifIFD0Directory.TAG_DATETIME, null, zone)
    return date?.let { LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault()) }
}
