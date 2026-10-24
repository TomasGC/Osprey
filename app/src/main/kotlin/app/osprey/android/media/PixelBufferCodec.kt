package app.osprey.android.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.osprey.media.PixelBuffer
import app.osprey.media.subsampleStepFor

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
private fun sampleSizeOf(context: Context, uri: Uri, maxSide: Int): Int {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        subsampleStepFor(bounds.outWidth, bounds.outHeight, maxSide)
    } else {
        1
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
