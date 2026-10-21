package app.osprey.android.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.osprey.media.PixelBuffer

/** Decodes the image at [uri] into a [PixelBuffer]. Returns null if it isn't a readable image. */
fun decodeImagePixelBuffer(context: Context, uri: Uri): PixelBuffer? {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    return input.use { BitmapFactory.decodeStream(it)?.toPixelBuffer() }
}

/** [Bitmap.getPixels] already returns `0xAARRGGBB` ints, the exact format [PixelBuffer] expects. */
fun Bitmap.toPixelBuffer(): PixelBuffer {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    recycle()
    return PixelBuffer(width, height, pixels)
}
