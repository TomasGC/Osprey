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
     * Decodes [entry] into a [PixelBuffer] for perceptual hashing: a full decode for an image,
     * one representative frame for a video. Returns null if [entry] is neither, or decoding
     * fails.
     */
    fun decodeFrame(entry: FileHandle): PixelBuffer?
}
