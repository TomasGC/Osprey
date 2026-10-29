package app.osprey.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val VALUE_BYTES = 2
private const val FAR_IFD0 = 1000

class TiffOrientationTest {

    private fun block(
        bigEndian: Boolean = false,
        orientation: Int? = 6,
        type: Int = TYPE_SHORT,
        count: Int = 1,
        thumbnail: Int? = null,
    ) = tiff(bigEndian, orientation, type, count, thumbnail)

    private fun located(data: ByteArray, tiff: Int = 0) = TiffOrientation.locate(data, tiff)!!

    @Test
    fun `reads a little endian value`() {
        val data = block(bigEndian = false, orientation = 6)
        assertEquals(ExifOrientation.ROTATE_90, TiffOrientation.read(data, located(data)))
    }

    @Test
    fun `reads a big endian value`() {
        val data = block(bigEndian = true, orientation = 8)
        assertEquals(ExifOrientation.ROTATE_270, TiffOrientation.read(data, located(data)))
    }

    @Test
    fun `finds the value in a block that starts inside a larger array`() {
        val data = ByteArray(10) { 9 } + block(orientation = 3)
        val found = located(data, tiff = 10)
        assertEquals(3, data[found.offset].toInt())
        assertEquals(ExifOrientation.ROTATE_180, TiffOrientation.read(data, found))
    }

    @Test
    fun `a block without Orientation has no value`() = assertNull(TiffOrientation.locate(block(orientation = null)))

    @Test
    fun `only IFD0 counts`() = assertNull(TiffOrientation.locate(block(orientation = null, thumbnail = 3)))

    @Test
    fun `a value that is not a short is refused`() = assertNull(TiffOrientation.locate(block(type = TYPE_LONG)))

    @Test
    fun `an entry holding several values is refused`() = assertNull(TiffOrientation.locate(block(count = 2)))

    @Test
    fun `a bad byte order mark or magic number is refused`() {
        val badOrder = block().also { it[0] = 0x58 }
        val badMagic = block().also { it[2] = 0x2B }
        assertNull(TiffOrientation.locate(badOrder))
        assertNull(TiffOrientation.locate(badMagic))
    }

    @Test
    fun `an IFD0 offset past the end of the data has no value`() {
        val data = block().also { it[4] = (FAR_IFD0 and 0xFF).toByte(); it[5] = (FAR_IFD0 shr 8).toByte() }
        assertNull(TiffOrientation.locate(data))
    }

    @Test
    fun `every prefix cut inside or before the value has none and never throws`() {
        val data = block()
        val end = located(data).offset + VALUE_BYTES - 1
        for (cut in 0..end) assertNull("cut at $cut", TiffOrientation.locate(data.copyOf(cut)))
    }

    @Test
    fun `an out of range stored value reads as normal`() {
        val data = block(orientation = 99)
        assertEquals(ExifOrientation.NORMAL, TiffOrientation.read(data, located(data)))
    }

    @Test
    fun `write changes only the value bytes in either byte order`() {
        for (bigEndian in listOf(false, true)) {
            val data = block(bigEndian = bigEndian, orientation = 1)
            val found = located(data)
            val written = TiffOrientation.write(data, found, ExifOrientation.ROTATE_270)
            val changed = data.indices.filter { data[it] != written[it] }
            assertEquals(listOf(if (bigEndian) found.offset + 1 else found.offset), changed)
            assertEquals(ExifOrientation.ROTATE_270, TiffOrientation.read(written, located(written)))
        }
    }

    @Test
    fun `write does not change its input and writing the same value gives equal bytes`() {
        val data = block(orientation = 6)
        val before = data.copyOf()
        val found = located(data)
        assertArrayEquals(data, TiffOrientation.write(data, found, ExifOrientation.ROTATE_90))
        TiffOrientation.write(data, found, ExifOrientation.NORMAL)
        assertArrayEquals(before, data)
    }
}
