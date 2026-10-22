package app.osprey.android.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.osprey.media.PixelBuffer

/** Converts a decoded frame into a Compose-renderable bitmap for thumbnails/preview. */
fun PixelBuffer.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap.asImageBitmap()
}
