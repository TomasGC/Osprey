package app.osprey.media

import app.osprey.filesystem.FileHandle

/**
 * Wraps a platform [MediaMetadataReader] so every frame comes back upright: cards, the viewer and the Visual Dupes
 * hash all decode through it (#51). The orientation is read only for a photo that decoded.
 */
class UprightMediaMetadataReader(
    private val decoder: MediaMetadataReader,
    private val orientations: OrientationSource,
) : MediaMetadataReader {

    override fun captureDateTime(entry: FileHandle): MediaDate? = decoder.captureDateTime(entry)

    override fun decodeFrame(entry: FileHandle, maxSide: Int?): PixelBuffer? =
        decoder.decodeFrame(entry, maxSide)?.oriented(orientations.orientationOf(entry))
}
