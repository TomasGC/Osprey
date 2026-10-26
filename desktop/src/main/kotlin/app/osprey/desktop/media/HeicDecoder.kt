package app.osprey.desktop.media

import app.osprey.media.PixelBuffer
import java.nio.file.Path

/** Extensions decoded through ffmpeg, which stitches a HEIC's tiles and applies its rotation itself. */
val heicExtensions = setOf(".heic")

/** Renders the whole photo at a path as PNG bytes, or null when it cannot be done: the seam tests replace. */
fun interface HeicRenderer {
    fun renderPng(path: Path): ByteArray?
}

/** [path] decoded whole and upright, subsampled to [maxSide] when given; null when it could not be rendered. */
fun decodeHeicPixelBuffer(path: Path, maxSide: Int? = null, renderer: HeicRenderer = FfmpegHeicRenderer): PixelBuffer? =
    renderer.renderPng(path)?.let { decodePngPixelBuffer(it, maxSide) }
