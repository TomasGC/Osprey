package app.osprey.desktop.media

import app.osprey.actions.heicExtensions
import app.osprey.actions.imageExtensions
import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.videoExtensions
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PixelBuffer
import java.nio.file.Path

/**
 * Real [MediaMetadataReader] for plain Windows/Unix paths: EXIF via `metadata-extractor`
 * ([readExifDateTime]), video-container metadata and frame extraction by shelling to
 * `ffprobe`/`ffmpeg` ([probeVideoCreationTime], [extractVideoFramePng]) — the same two binaries
 * on PATH the POC already required, bundling them is explicitly out of scope (#10). A HEIC photo is decoded
 * by `ffmpeg` too ([decodeHeicPixelBuffer]): `ImageIO` has no reader for it.
 */
class DesktopMediaMetadataReader(private val heicRenderer: HeicRenderer = FfmpegHeicRenderer) : MediaMetadataReader {

    override fun captureDateTime(entry: FileHandle): MediaDate? {
        val path = entry.path()
        return when (extensionOf(path)) {
            in imageExtensions -> readExifDateTime(path)?.let { MediaDate(it, MediaDateSource.EXIF) }
            in videoExtensions -> probeVideoCreationTime(path)?.let { MediaDate(it, MediaDateSource.VIDEO_METADATA) }
            else -> null
        }
    }

    override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? {
        val path = entry.path()
        return when (extensionOf(path)) {
            in heicExtensions -> decodeHeicPixelBuffer(path, maxSide, heicRenderer)
            in imageExtensions -> decodeImagePixelBuffer(path, maxSide)
            in videoExtensions -> extractVideoFramePng(path)?.let { decodePngPixelBuffer(it, maxSide) }
            else -> null
        }
    }

    private fun extensionOf(path: Path): String = splitStemAndSuffix(path.fileName.toString()).second.lowercase()

    private fun FileHandle.path(): Path = (this as PathHandle).path
}
