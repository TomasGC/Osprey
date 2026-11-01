package app.osprey.media

import app.osprey.fixtures.Photos
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORIENTATION_TAG = 0x0112
private const val SHORT_TYPE = 3
private const val RESERVED_AT_FROM_HDLR_TYPE = 8 // type, then the 4-byte handler type and 4 more bytes before the name
private const val SM_MODEL = "SM-S918B"
private const val ENTRY_VALUE_AT = 8
private const val ENTRY_COUNT_SIZE = 2
private const val ENTRY_SIZE = 12

/**
 * The guard on the real-photo fixtures (#76). A photo that goes into the private fixtures repository must carry
 * nothing but its pixels and an Orientation entry, whatever camera took it; [FixtureScrub] says what is left over.
 */
class FixtureScrubTest {

    /** [source] with its Exif item rewritten as the scrub does it: one Orientation entry, zeros after it. */
    private fun scrubbed(source: ByteArray): ByteArray {
        val layout = HeicLayout.of(source)
        val at = (layout.exif as ExifTarget.At).offset
        val extent = layout.extents.map { it.range(source, layout.idat)!! }.first { at in it }
        val value = source[at].toInt() and 0xFF
        val skip = source.u32(extent.first, true)
        val tiff = extent.first + 4 + skip
        val little = source[tiff] == 'I'.code.toByte()
        fun short(v: Int) = if (little) byteArrayOf(v.toByte(), (v shr 8).toByte()) else byteArrayOf((v shr 8).toByte(), v.toByte())
        fun int(v: Int) = if (little) byteArrayOf(v.toByte(), 0, 0, 0) else byteArrayOf(0, 0, 0, v.toByte())
        val minimal = (if (little) "II*".toByteArray() + 0 else "MM".toByteArray() + 0 + "*".toByteArray()) + int(8) +
            short(1) + short(ORIENTATION_TAG) + short(SHORT_TYPE) + int(1) + short(value) + short(0) + int(0)
        val out = source.copyOf()
        out.fill(0, tiff, extent.last + 1)
        minimal.copyInto(out, tiff)
        return out
    }

    private val clean get() = scrubbed(HeicSamples.rotated.bytes)

    @Test
    fun `a photo with a minimal Exif, three top-level boxes and a neutral name has nothing to report`() {
        assertEquals(emptyList<String>(), FixtureScrub.problems("gull_1.heic", clean))
        assertEquals(emptyList<String>(), FixtureScrub.problems("wasp_2.heic", scrubbed(HeicSamples.plain.bytes)))
    }

    @Test
    fun `an Exif with more than the orientation is reported`() {
        val problems = FixtureScrub.problems("gull_1.heic", HeicSamples.rotated.bytes)

        assertTrue(problems.toString(), problems.any { "Exif" in it })
    }

    @Test
    fun `data left in the Exif after the orientation entry is reported`() {
        val file = clean
        val layout = HeicLayout.of(file)
        val extent = layout.extents.map { it.range(file, layout.idat)!! }.first { (layout.exif as ExifTarget.At).offset in it }
        file[extent.last] = 'G'.code.toByte()

        assertTrue(FixtureScrub.problems("gull_1.heic", file).any { "Exif" in it })
    }

    @Test
    fun `an Exif with the orientation and one more entry is reported`() {
        val file = clean
        val orientationAt = (HeicLayout.of(file).exif as ExifTarget.At).offset
        val ifd = orientationAt - ENTRY_VALUE_AT - ENTRY_COUNT_SIZE
        file[ifd] = 2 // two entries (little endian)
        // a second entry (tag 0x0110, one short) right after the first
        byteArrayOf(0x10, 0x01, 3, 0, 1, 0, 0, 0, 1, 0, 0, 0).copyInto(file, ifd + ENTRY_COUNT_SIZE + ENTRY_SIZE)

        assertTrue(FixtureScrub.problems("gull_1.heic", file).any { "2 entries" in it })
    }

    @Test
    fun `a photo with no Exif item has nothing to report on that count`() {
        assertEquals(emptyList<String>(), FixtureScrub.problems("gull_1.heic", heic(exif = false).bytes))
    }

    @Test
    fun `an Exif item with no orientation entry cannot be checked, so it is reported`() {
        val problems = FixtureScrub.problems("gull_1.heic", heic(exifOrientation = null).bytes)

        assertTrue(problems.toString(), problems.any { "cannot be checked" in it })
    }

    @Test
    fun `the boxes a camera adds after meta are reported, whatever their content`() {
        val file = clean + heicBox("free", ByteArray(20)) + heicBox("sefd", ByteArray(8))

        val problems = FixtureScrub.problems("gull_1.heic", file)

        assertTrue(problems.toString(), problems.any { "free" in it && "sefd" in it })
    }

    @Test
    fun `a device model written into the meta box is reported`() {
        val file = clean
        val at = String(file, Charsets.ISO_8859_1).indexOf("hdlr") + RESERVED_AT_FROM_HDLR_TYPE + 8
        SM_MODEL.toByteArray().copyInto(file, at)

        val problems = FixtureScrub.problems("gull_1.heic", file)

        assertTrue(problems.toString(), problems.any { "device" in it })
    }

    @Test
    fun `a date, a GPS tag and an XMP packet in the meta box are each reported`() {
        for ((label, text) in mapOf("date" to "2026:07:25 ", "GPS" to "GPSInfo", "XMP" to "xpacket")) {
            val file = clean
            val at = String(file, Charsets.ISO_8859_1).indexOf("hdlr") + RESERVED_AT_FROM_HDLR_TYPE + 8
            text.toByteArray().copyInto(file, at)

            assertTrue(label, FixtureScrub.problems("gull_1.heic", file).any { label in it })
        }
    }

    @Test
    fun `the colour profile's own text is not a leak`() {
        // the Samsung profile carries "Samsung Electronics" in its description, the same on every phone (#76)
        val file = clean
        val profile = heicBox("colr", "prof".toByteArray() + "Copyright (c) 2022 Samsung Electronics Co., Ltd".toByteArray())
        val withProfile = insertInIpco(file, profile)

        assertEquals(emptyList<String>(), FixtureScrub.problems("gull_1.heic", withProfile))
    }

    @Test
    fun `a name that looks like a camera timestamp is reported`() {
        for (name in listOf("20260725_115212.heic", "IMG_2026.heic", "PXL_1.heic", "2026-07-25.heic", "vid_0001.heic")) {
            assertTrue(name, FixtureScrub.problems(name, clean).any { "name" in it })
        }
        for (name in listOf("gull_1.heic", "spider_wall_3.heic", "image.heic")) {
            assertEquals(name, emptyList<String>(), FixtureScrub.problems(name, clean))
        }
    }

    @Test
    fun `a file that is not a HEIC is reported, not thrown`() {
        assertTrue(FixtureScrub.problems("gull_1.heic", ByteArray(40)).any { "HEIC" in it })
    }

    @Test
    fun `every real fixture photo is scrubbed`() {
        val folder = Photos.need()
        val all = Photos.heic(folder)
        assertTrue("no photos in the heic folder of $folder", all.isNotEmpty())

        val reported = all.associate { it.fileName.toString() to FixtureScrub.problems(it.fileName.toString(), Files.readAllBytes(it)) }
            .filterValues { it.isNotEmpty() }

        assertEquals(emptyMap<String, List<String>>(), reported)
    }

    /** [file] with [box] added at the end of `ipco`, sizes of `ipco`, `iprp` and `meta` raised (association unchanged). */
    private fun insertInIpco(file: ByteArray, box: ByteArray): ByteArray {
        val layout = HeicLayout.of(file)
        val properties = checkNotNull(layout.properties)
        val at = properties.ipco.end
        val out = file.copyOf(file.size + box.size)
        file.copyInto(out, at + box.size, at, file.size)
        box.copyInto(out, at)
        for (parent in listOf(properties.ipco, properties.iprp, properties.meta)) out.putU32(parent.start, file.u32(parent.start, true) + box.size)
        return out
    }
}
