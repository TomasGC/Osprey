package app.osprey.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MEBIBYTE = 1024 * 1024
private const val THOROUGH = 6
private const val FAST = 3

class PngZlibTest {

    @Test
    fun `scanlines of up to 16 MiB get the thorough level`() {
        assertEquals(THOROUGH, PngZlib.compressionLevel(0))
        assertEquals(THOROUGH, PngZlib.compressionLevel(1))
        assertEquals(THOROUGH, PngZlib.compressionLevel(16 * MEBIBYTE))
    }

    @Test
    fun `bigger scanlines get the fast level`() {
        assertEquals(FAST, PngZlib.compressionLevel(16 * MEBIBYTE + 1))
        assertEquals(FAST, PngZlib.compressionLevel(36 * MEBIBYTE))
    }

    @Test
    fun `deflated data inflates back to the same bytes at either level`() {
        for (size in listOf(0, 1, 1000, 16 * MEBIBYTE + 1)) {
            val data = ByteArray(size) { (it % 251).toByte() }
            val inflated = PngZlib.inflate(listOf(PngChunk("IDAT", PngZlib.deflate(data))), size)
            assertArrayEquals("size $size", data, inflated.copyOf(size))
        }
    }
}
