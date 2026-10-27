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

    /** The `maxSide` of every [decodeFrame] call, in call order. */
    val requestedMaxSides = mutableListOf<Int?>()

    /** The region and sample size of every [decodeRegion] call, in call order. */
    val requestedRegions = mutableListOf<Pair<PhotoRegion, Int>>()

    fun setCaptureDateTime(entry: FileHandle, date: MediaDate) {
        dates[entry] = date
    }

    fun setFrame(entry: FileHandle, frame: PixelBuffer) {
        frames[entry] = frame
    }

    override fun captureDateTime(entry: FileHandle): MediaDate? = dates[entry]

    override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? {
        requestedMaxSides += maxSide
        return frames[entry]
    }

    override fun imageSize(entry: FileHandle): PhotoSize? = frames[entry]?.let { PhotoSize(it.width, it.height) }

    override fun decodeRegion(entry: FileHandle, region: PhotoRegion, sampleSize: Int): PixelBuffer? {
        requestedRegions += region to sampleSize
        return frames[entry]?.cropped(region)
    }
}
