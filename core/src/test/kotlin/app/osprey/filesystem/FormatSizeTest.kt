package app.osprey.filesystem

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSizeTest {

    @Test
    fun `bytes below one kilobyte`() {
        assertEquals("0 B", formatSize(0))
        assertEquals("1023 B", formatSize(1023))
    }

    @Test
    fun `kilobytes are whole numbers`() {
        assertEquals("1 KB", formatSize(1024))
        assertEquals("1023 KB", formatSize(1023L * 1024 + 1023))
    }

    @Test
    fun `megabytes and gigabytes have one decimal`() {
        assertEquals("1.0 MB", formatSize(1024L * 1024))
        assertEquals("1.5 MB", formatSize(1536L * 1024))
        assertEquals("2.0 GB", formatSize(2L * 1024 * 1024 * 1024))
    }
}
