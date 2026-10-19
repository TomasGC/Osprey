package app.osprey.desktop.media

import app.osprey.media.PixelBuffer
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO

/** Decodes the image at [path] into a [PixelBuffer]. Returns null if it isn't a readable image. */
@Suppress("SwallowedException") // Unreadable/corrupt input returns null, never crashes -- same as the POC.
fun decodeImagePixelBuffer(path: Path): PixelBuffer? = try {
    ImageIO.read(path.toFile())?.toPixelBuffer()
} catch (e: IOException) {
    null
}

/** Decodes raw [pngBytes] (e.g. a piped `ffmpeg` frame) into a [PixelBuffer]. */
@Suppress("SwallowedException")
fun decodePngPixelBuffer(pngBytes: ByteArray): PixelBuffer? = try {
    ImageIO.read(ByteArrayInputStream(pngBytes))?.toPixelBuffer()
} catch (e: IOException) {
    null
}

private fun BufferedImage.toPixelBuffer(): PixelBuffer {
    val pixels = IntArray(width * height)
    getRGB(0, 0, width, height, pixels, 0, width)
    return PixelBuffer(width, height, pixels)
}
