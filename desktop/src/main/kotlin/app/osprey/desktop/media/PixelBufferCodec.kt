package app.osprey.desktop.media

import app.osprey.media.PixelBuffer
import app.osprey.media.subsampleStepFor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.ImageInputStream

/**
 * Decodes the image at [path] into a [PixelBuffer], at full resolution or, with [maxSide], subsampled
 * while reading so the full image is never held in memory (#49). Returns null if it isn't a readable image.
 */
@Suppress("SwallowedException") // Unreadable/corrupt input returns null, never crashes -- same as the POC.
fun decodeImagePixelBuffer(path: Path, maxSide: Int? = null): PixelBuffer? = try {
    val image = if (maxSide == null) {
        ImageIO.read(path.toFile())
    } else {
        readSubsampled(ImageIO.createImageInputStream(path.toFile()), maxSide)
    }
    image?.toPixelBuffer()
} catch (e: IOException) {
    null
}

private fun readSubsampled(input: ImageInputStream?, maxSide: Int): BufferedImage? {
    if (input == null) return null
    return input.use { stream ->
        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) return@use null
        val reader = readers.next()
        try {
            reader.input = stream
            val step = subsampleStepFor(reader.getWidth(0), reader.getHeight(0), maxSide)
            val param = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
            reader.read(0, param)
        } finally {
            reader.dispose()
        }
    }
}

/** Decodes raw [pngBytes] (e.g. a piped `ffmpeg` frame) into a [PixelBuffer], subsampled to [maxSide] if given. */
@Suppress("SwallowedException")
fun decodePngPixelBuffer(pngBytes: ByteArray, maxSide: Int? = null): PixelBuffer? = try {
    val bytes = ByteArrayInputStream(pngBytes)
    val image = if (maxSide == null) {
        ImageIO.read(bytes)
    } else {
        readSubsampled(ImageIO.createImageInputStream(bytes), maxSide)
    }
    image?.toPixelBuffer()
} catch (e: IOException) {
    null
}

private fun BufferedImage.toPixelBuffer(): PixelBuffer {
    val pixels = IntArray(width * height)
    getRGB(0, 0, width, height, pixels, 0, width)
    return PixelBuffer(width, height, pixels)
}
