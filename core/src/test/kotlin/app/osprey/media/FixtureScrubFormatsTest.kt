package app.osprey.media

import app.osprey.fixtures.Photos
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXIF_ORIENTATION_TAG = 0x0112
private const val MODEL_TAG = 0x0110
private const val TIFF_SHORT = 3
private const val SOI = 0xD8
private const val EOI = 0xD9
private const val SOS = 0xDA
private const val DQT = 0xDB
private const val APP0 = 0xE0
private const val APP1 = 0xE1
private const val COMMENT = 0xFE
private val SIGNATURE_OF_PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

/** The guard on the JPEG and PNG fixtures (#76): pixels and, for a JPEG, one Orientation entry; nothing else. */
class FixtureScrubFormatsTest {

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun u32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun entry(tag: Int, value: Int) = u16(tag) + u16(TIFF_SHORT) + u32(1) + u16(value) + u16(0)

    /** An APP1 Exif body (big endian) with [tags] as entries and [extra] bytes after the IFD. */
    private fun exifBody(tags: List<Int> = listOf(EXIF_ORIENTATION_TAG), extra: ByteArray = ByteArray(0)) =
        "Exif".toByteArray() + byteArrayOf(0, 0) + "MM".toByteArray() + u16(42) + u32(8) + u16(tags.size) +
            tags.map { entry(it, 6) }.fold(ByteArray(0)) { all, one -> all + one } + u32(0) + extra

    private fun segment(marker: Int, body: ByteArray) = byteArrayOf(0xFF.toByte(), marker.toByte()) + u16(body.size + 2) + body

    private val jfif = segment(APP0, "JFIF".toByteArray() + byteArrayOf(0, 1, 1, 0, 0, 1, 0, 1, 0, 0))

    private fun jpeg(vararg segments: ByteArray) = byteArrayOf(0xFF.toByte(), SOI.toByte()) +
        segments.fold(ByteArray(0)) { all, one -> all + one } +
        segment(DQT, ByteArray(65)) + segment(SOS, ByteArray(6)) + byteArrayOf(0x12, 0x34, 0xFF.toByte(), EOI.toByte())

    private fun chunk(type: String, data: ByteArray = ByteArray(0)) = u32(data.size) + type.toByteArray() + data + u32(0)

    private fun png(vararg chunks: ByteArray) = SIGNATURE_OF_PNG + chunk("IHDR", ByteArray(13)) +
        chunks.fold(ByteArray(0)) { all, one -> all + one } + chunk("IDAT", ByteArray(4)) + chunk("IEND")

    @Test
    fun `a JPEG with no metadata, or with one Orientation entry only, has nothing to report`() {
        assertEquals(emptyList<String>(), FixtureScrub.problems("gull_1.jpg", jpeg(jfif)))
        assertEquals(emptyList<String>(), FixtureScrub.problems("gull_1_tag6.jpg", jpeg(jfif, segment(APP1, exifBody()))))
        assertEquals(emptyList<String>(), FixtureScrub.problems("gull_1_tag8.JPEG", jpeg(segment(APP1, exifBody()))))
    }

    @Test
    fun `a JPEG Exif with another entry, data after it or no orientation is reported`() {
        val more = FixtureScrub.problems("gull_1.jpg", jpeg(segment(APP1, exifBody(listOf(EXIF_ORIENTATION_TAG, MODEL_TAG)))))
        val after = FixtureScrub.problems("gull_1.jpg", jpeg(segment(APP1, exifBody(extra = "2026:07:25".toByteArray()))))
        val none = FixtureScrub.problems("gull_1.jpg", jpeg(segment(APP1, exifBody(listOf(MODEL_TAG)))))

        assertTrue(more.toString(), more.any { "Exif" in it && "2 entries" in it })
        assertTrue(after.toString(), after.any { "Exif" in it && "after" in it })
        assertTrue(none.toString(), none.any { "Exif" in it && "Orientation" in it })
    }

    @Test
    fun `a JPEG with a comment, an XMP packet or any other segment is reported, whatever it holds`() {
        val comment = FixtureScrub.problems("a_1.jpg", jpeg(jfif, segment(COMMENT, "hello".toByteArray())))
        val xmp = FixtureScrub.problems("a_1.jpg", jpeg(jfif, segment(APP1, "http://ns.adobe.com/xap/1.0/".toByteArray())))

        assertTrue(comment.toString(), comment.any { "FFFE" in it })
        assertTrue(xmp.toString(), xmp.any { "FFE1" in it })
    }

    @Test
    fun `a JPEG that is not one, or is cut before its picture, is reported, not thrown`() {
        assertTrue(FixtureScrub.problems("a_1.jpg", ByteArray(40)).any { "JPEG" in it })
        assertTrue(FixtureScrub.problems("a_1.jpg", jpeg(jfif).copyOf(30)).any { "JPEG" in it })
    }

    @Test
    fun `a PNG with only its image chunks and the colour ones has nothing to report`() {
        assertEquals(emptyList<String>(), FixtureScrub.problems("gull_1.png", png()))
        assertEquals(emptyList<String>(), FixtureScrub.problems("gull_1.png", png(chunk("sRGB", ByteArray(1)), chunk("pHYs", ByteArray(9)))))
    }

    @Test
    fun `a PNG text, Exif or time chunk is reported`() {
        for (type in listOf("tEXt", "zTXt", "iTXt", "eXIf", "tIME")) {
            val problems = FixtureScrub.problems("gull_1.png", png(chunk(type, "x".toByteArray())))

            assertTrue("$type: $problems", problems.any { type in it })
        }
    }

    @Test
    fun `a PNG that is not one, or is cut short, is reported, not thrown`() {
        assertTrue(FixtureScrub.problems("a_1.png", ByteArray(40)).any { "PNG" in it })
        assertTrue(FixtureScrub.problems("a_1.png", png().copyOf(30)).any { "PNG" in it })
    }

    @Test
    fun `a JPEG or PNG with a camera file name is reported`() {
        assertTrue(FixtureScrub.problems("IMG_2026.jpg", jpeg(jfif)).any { "name" in it })
        assertTrue(FixtureScrub.problems("20260725_115212.png", png()).any { "name" in it })
    }

    @Test
    fun `a file of another format is reported`() {
        assertTrue(FixtureScrub.problems("a_1.bmp", ByteArray(40)).any { "format" in it })
    }

    @Test
    fun `every real fixture JPEG and PNG is scrubbed`() {
        val folder = Photos.need()
        val all = Photos.jpeg(folder) + Photos.png(folder)
        assertTrue("no photos in the jpg and png folders of $folder", all.isNotEmpty())

        val reported = all.associate { it.fileName.toString() to FixtureScrub.problems(it.fileName.toString(), Files.readAllBytes(it)) }
            .filterValues { it.isNotEmpty() }

        assertEquals(emptyMap<String, List<String>>(), reported)
    }
}
