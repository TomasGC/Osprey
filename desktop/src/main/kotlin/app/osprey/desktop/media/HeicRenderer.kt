package app.osprey.desktop.media

import app.osprey.media.PixelBuffer
import java.nio.file.Path

/** Renders the whole photo at a path as picture bytes (BMP or PNG), or null when it cannot: the seam tests replace. */
fun interface HeicRenderer {
    fun render(path: Path): ByteArray?
}

/** [path] decoded whole and upright, subsampled to [maxSide] when given; null when it could not be rendered. */
fun decodeHeicPixelBuffer(path: Path, maxSide: Int? = null, renderer: HeicRenderer = FfmpegHeicRenderer): PixelBuffer? =
    renderer.render(path)?.let { decodeBytesPixelBuffer(it, maxSide) }
