package app.osprey.desktop.media

import app.osprey.actions.heicExtensions
import app.osprey.actions.imageExtensions
import app.osprey.actions.splitStemAndSuffix
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.media.ExifOrientation
import app.osprey.media.OrientationSource
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import java.nio.file.Path

/**
 * Orientation of a photo on plain paths: the EXIF Orientation tag, read by `metadata-extractor` (a JPEG, and the PNG
 * EXIF chunk). A HEIC and a video frame come out of ffmpeg already upright, so they answer normal without being read.
 */
class DesktopOrientationSource : OrientationSource {

    override fun orientationOf(entry: FileHandle): ExifOrientation {
        val path = (entry as PathHandle).path
        val extension = splitStemAndSuffix(path.fileName.toString()).second.lowercase()
        val storesOrientation = extension in imageExtensions && extension !in heicExtensions
        return if (storesOrientation) readOrientation(path) else ExifOrientation.NORMAL
    }

    // An unreadable image must never crash a scan: it is shown as stored.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun readOrientation(path: Path): ExifOrientation = try {
        val metadata = ImageMetadataReader.readMetadata(path.toFile())
        val exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        ExifOrientation.fromExif(exif?.getInteger(ExifIFD0Directory.TAG_ORIENTATION))
    } catch (e: Exception) {
        ExifOrientation.NORMAL
    }
}
