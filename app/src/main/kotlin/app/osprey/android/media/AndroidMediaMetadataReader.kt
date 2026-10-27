package app.osprey.android.media

import android.content.Context
import android.net.Uri
import app.osprey.actions.imageExtensions
import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.videoExtensions
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PhotoRegion
import app.osprey.media.PhotoSize
import app.osprey.media.PixelBuffer

/**
 * Real [MediaMetadataReader] over the Storage Access Framework: EXIF via `androidx.exifinterface`
 * ([readExifDateTime]), video-container metadata and frame extraction via the platform's own
 * [android.media.MediaMetadataRetriever] ([probeVideoCreationTime], [extractVideoFrameBitmap]) --
 * no ffmpeg dependency, confirmed unnecessary after `ffmpeg-kit-android` turned out to be an
 * archived, unmaintained library (#13). The size of a photo comes from its header and a region of it from
 * [android.graphics.BitmapRegionDecoder] ([readImageSize], [decodeImageRegion]) (#60).
 */
class AndroidMediaMetadataReader(private val context: Context) : MediaMetadataReader {

    override fun captureDateTime(entry: FileHandle): MediaDate? {
        val doc = entry.documentFile()
        return when (extensionOf(doc.name)) {
            in imageExtensions -> readExifDateTime(context, doc.uri)?.let { MediaDate(it, MediaDateSource.EXIF) }
            in videoExtensions ->
                probeVideoCreationTime(context, doc.uri)?.let { MediaDate(it, MediaDateSource.VIDEO_METADATA) }
            else -> null
        }
    }

    override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? {
        val doc = entry.documentFile()
        return when (extensionOf(doc.name)) {
            in imageExtensions -> decodeImagePixelBuffer(context, doc.uri, maxSide)
            in videoExtensions -> extractVideoFrameBitmap(context, doc.uri)?.let { frame ->
                (maxSide?.let(frame::scaledToMaxSide) ?: frame).toPixelBuffer()
            }
            else -> null
        }
    }

    override fun imageSize(entry: FileHandle): PhotoSize? = entry.imageUri()?.let { readImageSize(context, it) }

    override fun decodeRegion(entry: FileHandle, region: PhotoRegion, sampleSize: Int): PixelBuffer? =
        entry.imageUri()?.let { decodeImageRegion(context, it, region, sampleSize) }

    /** The URI of [this] when it is a photo; a video has no size to read and no region to decode. */
    private fun FileHandle.imageUri(): Uri? = documentFile().takeIf { extensionOf(it.name) in imageExtensions }?.uri

    private fun extensionOf(name: String?): String = splitStemAndSuffix(name.orEmpty()).second.lowercase()

    private fun FileHandle.documentFile() = (this as AndroidFileHandle).documentFile
}
