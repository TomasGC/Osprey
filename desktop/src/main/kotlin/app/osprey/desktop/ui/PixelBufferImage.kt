package app.osprey.desktop.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.osprey.media.PixelBuffer
import java.awt.image.BufferedImage

/** Converts a decoded frame into a Compose-renderable bitmap for thumbnails/preview. */
fun PixelBuffer.toImageBitmap(): ImageBitmap {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, pixels, 0, width)
    return image.toComposeImageBitmap()
}
