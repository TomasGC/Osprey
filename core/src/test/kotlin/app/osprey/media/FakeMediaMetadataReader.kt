package app.osprey.media

import app.osprey.filesystem.FileHandle

/**
 * In-memory [MediaMetadataReader]: returns whatever was pre-registered for a given [FileHandle]
 * via [setCaptureDateTime]/[setFrame], null otherwise — for `:core` planners whose tests need
 * EXIF/video-metadata/pixel results without decoding anything for real.
 */
class FakeMediaMetadataReader : MediaMetadataReader {

    private val dates = mutableMapOf<FileHandle, MediaDate>()
    private val frames = mutableMapOf<FileHandle, PixelBuffer>()

    fun setCaptureDateTime(entry: FileHandle, date: MediaDate) {
        dates[entry] = date
    }

    fun setFrame(entry: FileHandle, frame: PixelBuffer) {
        frames[entry] = frame
    }

    override fun captureDateTime(entry: FileHandle): MediaDate? = dates[entry]

    override fun decodeFrame(entry: FileHandle): PixelBuffer? = frames[entry]
}
