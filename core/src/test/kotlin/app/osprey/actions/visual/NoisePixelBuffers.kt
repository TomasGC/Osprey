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

/**
 * [base] with a small fraction of its pixels re-randomized -- stands in for "the same scene,
 * a genuinely different shot" (issue #26): close enough to still look similar, far enough that
 * it must not join a dupe group. Calibrated empirically against real photos of exactly this kind
 * (#26): `fraction = 0.02` reliably lands around the real measured distance (4-6), comfortably
 * above [VISUAL_HAMMING_THRESHOLD] and comfortably below a same-seed/different-seed [noisePixelBuffer] pair.
 */
fun perturbedPixelBuffer(base: PixelBuffer, seed: Int, fraction: Double = 0.02): PixelBuffer {
    val random = Random(seed)
    val pixels = base.pixels.copyOf()
    for (i in pixels.indices) {
        if (random.nextDouble() < fraction) {
            val red = random.nextInt(ALPHA_OPAQUE + 1)
            val green = random.nextInt(ALPHA_OPAQUE + 1)
            val blue = random.nextInt(ALPHA_OPAQUE + 1)
            pixels[i] = (ALPHA_OPAQUE shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    return PixelBuffer(base.width, base.height, pixels)
}
