package app.osprey.actions.visual

import app.osprey.media.PixelBuffer
import kotlin.random.Random

private const val ALPHA_OPAQUE = 0xFF

/**
 * A flat solid-color image has no AC/DCT content, so [phash] collapses every solid color onto
 * the same hash — useless for distinctness tests. Structured noise gives each seed a genuinely
 * different hash, same rationale as the POC's `_make_noise_image` test helper.
 */
fun noisePixelBuffer(seed: Int, size: Int = 32): PixelBuffer {
    val random = Random(seed)
    val pixels = IntArray(size * size) {
        val red = random.nextInt(ALPHA_OPAQUE + 1)
        val green = random.nextInt(ALPHA_OPAQUE + 1)
        val blue = random.nextInt(ALPHA_OPAQUE + 1)
        (ALPHA_OPAQUE shl 24) or (red shl 16) or (green shl 8) or blue
    }
    return PixelBuffer(size, size, pixels)
}
