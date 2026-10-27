package app.osprey.media

import app.osprey.filesystem.FileHandle

/**
 * Says how a photo must be turned to show it upright, read in the format's own way by each platform (EXIF, the PNG
 * EXIF chunk, a HEIC rotation property). Answers [ExifOrientation.NORMAL] when a decoder has already turned the pixels.
 */
fun interface OrientationSource {
    fun orientationOf(entry: FileHandle): ExifOrientation
}
