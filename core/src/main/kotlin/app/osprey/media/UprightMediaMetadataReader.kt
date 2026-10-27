package app.osprey.media

import app.osprey.filesystem.FileHandle

/**
 * Wraps a platform [MediaMetadataReader] so every frame comes back upright: cards, the viewer and the Visual Dupes
 * hash all decode through it (#51), and so do the size and the regions of the zoomed detail (#60). The orientation is
 * read only for a photo that decoded or whose size is known.
 */
class UprightMediaMetadataReader(
    private val decoder: MediaMetadataReader,
    private val orientations: OrientationSource,
) : MediaMetadataReader {

    override fun captureDateTime(entry: FileHandle): MediaDate? = decoder.captureDateTime(entry)

    override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? =
        decoder.decodeFrame(entry, maxSide)?.oriented(orientations.orientationOf(entry))

    override fun imageSize(entry: FileHandle): PhotoSize? =
        decoder.imageSize(entry)?.oriented(orientations.orientationOf(entry))

    /** The region is mapped to the stored pixels, decoded there, then turned: the tile matches the upright view. */
    override fun decodeRegion(entry: FileHandle, region: PhotoRegion, sampleSize: Int): PixelBuffer? {
        val stored = decoder.imageSize(entry) ?: return null
        val orientation = orientations.orientationOf(entry)
        val upright = stored.oriented(orientation)
        val storedRegion = region.toStored(orientation, upright.width, upright.height)
        return decoder.decodeRegion(entry, storedRegion, sampleSize)?.oriented(orientation)
    }

    private fun PhotoSize.oriented(orientation: ExifOrientation) =
        if (orientation.swapsDimensions) PhotoSize(height, width) else this
}
