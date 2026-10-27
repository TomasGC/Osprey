package app.osprey.media

import app.osprey.filesystem.FileHandle

/**
 * Platform-specific media decoding: EXIF/video-container metadata and pixel decoding, neither of
 * which `:core` can do on its own (no EXIF parser, no video decoder, no image library).
 */
interface MediaMetadataReader {

    /**
     * The capture datetime recorded in [entry]'s own content: EXIF for an image, container
     * creation time for a video. Returns null if [entry] is neither, has no such metadata, or
     * it's unparseable. Never falls back to the filename — see [MediaDateSource].
     */
    fun captureDateTime(entry: FileHandle): MediaDate?

    /**
     * Decodes [entry] into a [PixelBuffer]: an image, or one representative frame for a video.
     * Returns null if [entry] is neither, or decoding fails.
     *
     * [maxSide] null decodes at full resolution. Otherwise the longest side of the result is at
     * least [maxSide] when the source is that large (never upscaled), and at most about twice
     * it: platforms subsample in whole steps rather than resizing exactly.
     */
    fun decodeFrame(entry: FileHandle, maxSide: Int? = null): PixelBuffer?

    /**
     * The size in pixels of the image at [entry], read without decoding it; null when it is not an image or the size
     * is unreadable. A platform reader gives the stored size, [UprightMediaMetadataReader] the upright one (#60).
     */
    fun imageSize(entry: FileHandle): PhotoSize? = null

    /**
     * Decodes only [region] of the image at [entry], subsampled by the power of two [sampleSize] (1 = every pixel), so
     * a large photo is never held whole (#60). The region lies within [imageSize] and is in the pixels that reader
     * answers in: stored for a platform reader, upright for [UprightMediaMetadataReader]. Null when the image cannot be
     * read this way (a video, a format the decoder refuses); the caller then keeps its smaller picture.
     */
    fun decodeRegion(entry: FileHandle, region: PhotoRegion, sampleSize: Int): PixelBuffer? = null
}
