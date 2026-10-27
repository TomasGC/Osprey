package app.osprey.android.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import app.osprey.media.PhotoRegion
import app.osprey.media.PhotoSize
import app.osprey.media.PixelBuffer
import app.osprey.media.subsampleStepFor
import java.io.FileDescriptor
import java.io.IOException

/**
 * Decodes the image at [uri] into a [PixelBuffer], at full resolution or, with [maxSide], subsampled by the
 * decoder so the full-size bitmap is never allocated (#49). Returns null if it isn't a readable image.
 */
fun decodeImagePixelBuffer(context: Context, uri: Uri, maxSide: Int? = null): PixelBuffer? {
    val options = maxSide?.let { BitmapFactory.Options().apply { inSampleSize = sampleSizeOf(context, uri, it) } }
    val input = context.contentResolver.openInputStream(uri) ?: return null
    return input.use { BitmapFactory.decodeStream(it, null, options)?.toPixelBuffer() }
}

/** Reads only the image header of [uri] to pick the subsampling for [maxSide]; 1 if the size is unreadable. */
private fun sampleSizeOf(context: Context, uri: Uri, maxSide: Int): Int =
    readImageSize(context, uri)?.let { subsampleStepFor(it.width, it.height, maxSide) } ?: 1

/** The stored size of the image at [uri], from its header only; null if it cannot be read. */
fun readImageSize(context: Context, uri: Uri): PhotoSize? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) PhotoSize(bounds.outWidth, bounds.outHeight) else null
}

/**
 * Decodes only [region] of the image at [uri], in stored pixels, subsampled by [sampleSize]: the rest of the photo is
 * never allocated (#60). Null if the file cannot be read this way or the region lies outside the image.
 */
// An image the region decoder refuses keeps its smaller picture: the caller needs only to know there is no detail.
@Suppress("SwallowedException")
fun decodeImageRegion(context: Context, uri: Uri, region: PhotoRegion, sampleSize: Int): PixelBuffer? = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { decodeRegionOf(it.fileDescriptor, region, sampleSize) }
} catch (e: IOException) {
    null
} catch (e: IllegalArgumentException) {
    null
}

// The replacement overload starts at API 31; minSdk is 26.
@Suppress("DEPRECATION")
private fun decodeRegionOf(descriptor: FileDescriptor, region: PhotoRegion, sampleSize: Int): PixelBuffer? {
    val decoder = BitmapRegionDecoder.newInstance(descriptor, false)
    try {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bounds = Rect(region.left, region.top, region.left + region.width, region.top + region.height)
        return decoder.decodeRegion(bounds, options)?.toPixelBuffer()
    } finally {
        decoder.recycle()
    }
}

/** [this] shrunk so its longest side is [maxSide], or [this] untouched if it is already that small. */
fun Bitmap.scaledToMaxSide(maxSide: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val scale = maxSide.toFloat() / longest
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    recycle()
    return scaled
}

/** [Bitmap.getPixels] already returns `0xAARRGGBB` ints, the exact format [PixelBuffer] expects. */
fun Bitmap.toPixelBuffer(): PixelBuffer {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    recycle()
    return PixelBuffer(width, height, pixels)
}
