package app.osprey.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val VALUE_BYTES = 2

class JpegOrientationTest {

    private fun differingPositions(a: ByteArray, b: ByteArray) = a.indices.filter { a[it] != b[it] }

    @Test
    fun `reads a little endian value`() =
        assertEquals(ExifOrientation.ROTATE_90, JpegOrientation.read(jpegWith(bigEndian = false, orientation = 6)))

    @Test
    fun `reads a big endian value`() =
        assertEquals(ExifOrientation.ROTATE_270, JpegOrientation.read(jpegWith(bigEndian = true, orientation = 8)))

    @Test
    fun `the offset points at the value after the tags that come before it`() {
        val jpeg = jpegWith(orientation = 3)
        val offset = JpegOrientation.valueOffset(jpeg)
        assertNotNull(offset)
        assertEquals(3, jpeg[offset!!].toInt())
    }

    @Test
    fun `skips the JFIF and XMP segments before the Exif one`() =
        assertEquals(ExifOrientation.ROTATE_180, JpegOrientation.read(jpegWith(orientation = 3)))

    @Test
    fun `a file with no Exif segment has no value`() = assertNull(JpegOrientation.valueOffset(jpegWith(withExif = false)))

    @Test
    fun `an Exif block without Orientation has no value`() =
        assertNull(JpegOrientation.valueOffset(jpegWith(orientation = null)))

    @Test
    fun `only IFD0 counts`() =
        assertNull(JpegOrientation.valueOffset(jpegWith(orientation = null, thumbnail = 3)))

    @Test
    fun `only IFD0 is changed`() {
        val jpeg = jpegWith(orientation = 6, thumbnail = 3)
        val written = JpegOrientation.rewrite(jpeg) { ExifOrientation.NORMAL }!!
        val offset = JpegOrientation.valueOffset(jpeg)!!
        assertEquals(listOf(offset), differingPositions(jpeg, written))
    }

    @Test
    fun `a value that is not a short is refused`() =
        assertNull(JpegOrientation.valueOffset(jpegWith(orientation = 6, type = TYPE_LONG)))

    @Test
    fun `not a JPEG has no value`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
        assertNull(JpegOrientation.valueOffset(png))
        assertNull(JpegOrientation.valueOffset(ByteArray(0)))
    }

    @Test
    fun `a bad byte order mark or magic number is refused`() {
        val jpeg = jpegWith()
        val tiffStart = jpeg.indexOfSequence("Exif\u0000\u0000".toByteArray()) + "Exif\u0000\u0000".length
        val badOrder = jpeg.copyOf().also { it[tiffStart] = 'X'.code.toByte() }
        val badMagic = jpeg.copyOf().also { it[tiffStart + 2] = 43 }
        assertNull(JpegOrientation.valueOffset(badOrder))
        assertNull(JpegOrientation.valueOffset(badMagic))
    }

    @Test
    fun `a segment with length zero is refused`() {
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0, 0, 1, 2, 3)
        assertNull(JpegOrientation.valueOffset(corrupt))
    }

    @Test
    fun `every prefix cut before the value has none and never throws`() {
        val jpeg = jpegWith()
        val end = JpegOrientation.valueOffset(jpeg)!! + VALUE_BYTES
        for (length in 0 until end) {
            assertNull("cut at $length", JpegOrientation.valueOffset(jpeg.copyOf(length)))
        }
        assertNotNull(JpegOrientation.valueOffset(jpeg.copyOf(end)))
    }

    @Test
    fun `an out of range stored value reads as normal and can be replaced`() {
        val jpeg = jpegWith(orientation = 9)
        assertEquals(ExifOrientation.NORMAL, JpegOrientation.read(jpeg))
        assertEquals(ExifOrientation.ROTATE_90, JpegOrientation.read(JpegOrientation.rewrite(jpeg) { ExifOrientation.ROTATE_90 }!!))
    }

    @Test
    fun `write changes only the value bytes`() {
        for (bigEndian in listOf(false, true)) {
            val jpeg = jpegWith(bigEndian = bigEndian, orientation = 6)
            val offset = JpegOrientation.valueOffset(jpeg)!!
            for (target in ExifOrientation.entries) {
                val written = JpegOrientation.rewrite(jpeg) { target }!!
                assertEquals(jpeg.size, written.size)
                assertEquals(true, differingPositions(jpeg, written).all { it in offset until offset + VALUE_BYTES })
                assertArrayEquals(jpeg.copyOfRange(offset + VALUE_BYTES, jpeg.size), written.copyOfRange(offset + VALUE_BYTES, jpeg.size))
                assertEquals(target, JpegOrientation.read(written))
            }
        }
    }

    @Test
    fun `write does not change its input`() {
        val jpeg = jpegWith(orientation = 6)
        val before = jpeg.copyOf()
        JpegOrientation.rewrite(jpeg) { ExifOrientation.ROTATE_180 }
        assertArrayEquals(before, jpeg)
    }

    @Test
    fun `write of the same value returns equal bytes`() {
        val jpeg = jpegWith(orientation = 6)
        assertArrayEquals(jpeg, JpegOrientation.rewrite(jpeg) { ExifOrientation.ROTATE_90 })
    }

    @Test
    fun `write returns null when there is no value to change`() {
        assertNull(JpegOrientation.rewrite(jpegWith(orientation = null)) { ExifOrientation.ROTATE_90 })
        assertNull(JpegOrientation.rewrite(ByteArray(0)) { ExifOrientation.ROTATE_90 })
    }

    @Test
    fun `read returns null when there is no value`() = assertNull(JpegOrientation.read(jpegWith(orientation = null)))

    @Test
    fun `an entry holding several values is refused`() =
        assertNull(JpegOrientation.valueOffset(jpegWith(orientation = 6, count = 2)))

    /** A 6-byte block that is not a segment: its length field points straight at the Exif segment after it. */
    @Test
    fun `garbage where a segment should start has no value`() {
        val garbage = byteArrayOf(0x12, 0x34, 0x00, 0x04, 0x00, 0x00)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + garbage + exifSegment()
        assertNull(JpegOrientation.valueOffset(jpeg))
    }

    @Test
    fun `nothing after the scan header is read as a segment`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + segment(0xDA, byteArrayOf(0, 0)) + exifSegment()
        assertNull(JpegOrientation.valueOffset(jpeg))
    }

    private fun ByteArray.indexOfSequence(needle: ByteArray): Int =
        indices.first { start -> needle.indices.all { this[start + it] == needle[it] } }
}
