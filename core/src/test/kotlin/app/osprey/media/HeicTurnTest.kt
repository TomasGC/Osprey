package app.osprey.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** irot counts anticlockwise quarter turns; this is the EXIF Orientation that shows the same picture. */
private val EXIF_OF_IROT = mapOf(0 to 1, 1 to 8, 2 to 3, 3 to 6)
private val TURNS = listOf(1, 2, 3, 5, -1, -2, -3)

class HeicTurnTest {

    private fun patched(file: ByteArray, quarterTurns: Int): HeicTurnResult.Patched =
        when (val result = HeicTurn.turn(file, quarterTurns)) {
            is HeicTurnResult.Patched -> result
            else -> throw AssertionError("expected edits, got $result")
        }

    private fun refusal(file: ByteArray, quarterTurns: Int = 1): String = when (val result = HeicTurn.turn(file, quarterTurns)) {
        is HeicTurnResult.Refused -> result.reason
        else -> throw AssertionError("expected a refusal, got $result")
    }

    private fun applied(file: ByteArray, result: HeicTurnResult.Patched): ByteArray =
        file.copyOf().also { out -> result.edits.forEach { it.bytes.copyInto(out, it.offset) } }

    private fun exifValue(bytes: ByteArray, at: Int, bigEndian: Boolean): Int =
        if (bigEndian) (bytes[at].toInt() and 0xFF shl 8) or (bytes[at + 1].toInt() and 0xFF)
        else (bytes[at + 1].toInt() and 0xFF shl 8) or (bytes[at].toInt() and 0xFF)

    private fun angleAfter(angle: Int, turns: Int) = (angle - turns).mod(4)

    // --- what a turn writes

    @Test
    fun `a turn writes the irot angle minus the turns, wrapping round`() {
        for (angle in 0..3) {
            for (turns in TURNS) {
                val built = heic(primaryIrot = angle, exifOrientation = EXIF_OF_IROT.getValue(angle))
                val out = applied(built.bytes, patched(built.bytes, turns))
                assertEquals("angle $angle, $turns turns", angleAfter(angle, turns), out[built.irotAt!!].toInt())
            }
        }
    }

    @Test
    fun `the Exif orientation is written to match, in either byte order`() {
        for (bigEndian in listOf(false, true)) {
            for (angle in 0..3) {
                val built = heic(primaryIrot = angle, exifOrientation = EXIF_OF_IROT.getValue(angle), exifBigEndian = bigEndian)
                val out = applied(built.bytes, patched(built.bytes, 1))
                assertEquals(
                    "angle $angle, big endian $bigEndian",
                    EXIF_OF_IROT.getValue(angleAfter(angle, 1)),
                    exifValue(out, built.exifValueAt!!, bigEndian),
                )
            }
        }
    }

    @Test
    fun `nothing but the irot byte and the two Exif bytes changes`() {
        val built = heic()
        val result = patched(built.bytes, 1)
        val out = applied(built.bytes, result)

        val changed = built.bytes.indices.filter { built.bytes[it] != out[it] }.toSet()

        assertTrue(changed.isNotEmpty())
        assertTrue(changed.all { it == built.irotAt || it == built.exifValueAt || it == built.exifValueAt!! + 1 })
        assertTrue(result.edits.all { it.offset == built.irotAt || it.offset == built.exifValueAt })
    }

    @Test
    fun `an Exif that disagrees with irot is made to agree`() {
        val built = heic(primaryIrot = 3, exifOrientation = 1)

        val out = applied(built.bytes, patched(built.bytes, 1))

        assertEquals(EXIF_OF_IROT.getValue(2), exifValue(out, built.exifValueAt!!, false))
    }

    @Test
    fun `a photo with no Exif item gets only its irot written, with nothing to say`() {
        val built = heic(exif = false)

        val result = patched(built.bytes, 1)

        assertEquals(listOf(built.irotAt), result.edits.map { it.offset })
        assertNull(result.note)
    }

    @Test
    fun `an Exif with no orientation entry gets only irot, and the note says what that means`() {
        val built = heic(exifOrientation = null)

        val result = patched(built.bytes, 1)

        assertEquals(listOf(built.irotAt), result.edits.map { it.offset })
        assertEquals(HeicTurn.EXIF_NOTE, result.note)
    }

    @Test
    fun `the reserved bits of the irot byte are kept`() {
        val built = heic(primaryIrot = 0xF8 or 3)

        val out = applied(built.bytes, patched(built.bytes, 1))

        assertEquals(0xF8 or 2, out[built.irotAt!!].toInt() and 0xFF)
    }

    @Test
    fun `only the primary item's irot is turned, never a tile's`() {
        val built = heic(primaryIrot = 3, tileIrot = 1)

        val result = patched(built.bytes, 1)

        assertEquals(setOf(built.irotAt, built.exifValueAt), result.edits.map { it.offset }.toSet())
    }

    @Test
    fun `a photo with no irot on its primary item is refused for now`() {
        assertEquals(HeicTurn.NO_ROTATION, refusal(heic(primaryIrot = null).bytes))
        assertEquals(HeicTurn.NO_ROTATION, refusal(heic(primaryIrot = null, tileIrot = 1).bytes))
    }

    // --- every layout a real file can have

    @Test
    fun `the same edits are found whatever the layout of the file`() {
        val layouts = mapOf(
            "ipma version 1" to heic(ipmaVersion = 1),
            "ipma 2-byte associations" to heic(ipmaFlags = 1),
            "ipma version 1 and 2-byte associations" to heic(ipmaVersion = 1, ipmaFlags = 1),
            "iloc version 0" to heic(ilocVersion = 0),
            "iloc version 2" to heic(ilocVersion = 2),
            "meta before mdat" to heic(metaFirst = true),
            "64-bit mdat size" to heic(largeMdat = true),
            "boxes after meta" to heic(trailer = heicBox("free", ByteArray(40)) + heicBox("sefd", ByteArray(30))),
            "Exif in idat" to heic(exifInIdat = true),
            "big-endian Exif" to heic(exifBigEndian = true),
        )
        for ((name, built) in layouts) assertTurnedOnce(name, built, bigEndian = name == "big-endian Exif")
    }

    private fun assertTurnedOnce(name: String, built: BuiltHeic, bigEndian: Boolean = false) {
        val result = patched(built.bytes, 1)
        val out = applied(built.bytes, result)
        assertEquals(name, 2, out[built.irotAt!!].toInt())
        assertEquals(name, 3, exifValue(out, built.exifValueAt!!, bigEndian))
        assertEquals(name, setOf(built.irotAt, built.exifValueAt), result.edits.map { it.offset }.toSet())
    }

    @Test
    fun `the same edits are found with the rare layouts too`() {
        val lastBoxToTheEnd = int32(0) + "free".toByteArray() + ByteArray(20)
        val layouts = mapOf(
            "pitm version 1" to heic(quirks = Quirks(pitmVersion = 1)),
            "iinf version 1" to heic(quirks = Quirks(iinfVersion = 1)),
            "infe version 3" to heic(quirks = Quirks(infeVersion = 3)),
            "infe entries without a type before the Exif item" to heic(quirks = Quirks(legacyTileInfe = true)),
            "two extents, the first is the Exif" to heic(quirks = Quirks(twoExtents = true)),
            "8-byte iloc offsets" to heic(quirks = Quirks(ilocOffsetSize = 8)),
            "a meta box with a 64-bit size" to heic(quirks = Quirks(largeMeta = true)),
            "a last box of size 0, which runs to the end of the file" to heic(trailer = lastBoxToTheEnd),
        )
        for ((name, built) in layouts) assertTurnedOnce(name, built)
    }

    @Test
    fun `structure that contradicts itself is damage`() {
        val damaged = mapOf(
            "an empty irot box" to heic(quirks = Quirks(emptyIrot = true)),
            "an Exif missing from iloc" to heic(quirks = Quirks(ilocRows = listOf(2, 3))),
            "an iloc with no items" to heic(quirks = Quirks(ilocRows = emptyList())),
            "an Exif with no extent" to heic(quirks = Quirks(noExtents = true)),
            "an Exif in idat when there is no idat" to heic(exifInIdat = true, quirks = Quirks(idatMissing = true)),
            "an unknown construction method" to heic(quirks = Quirks(exifMethod = 2)),
            "a TIFF offset past the Exif" to heic(quirks = Quirks(exifTiffOffset = 1000)),
            "an Exif extent that ends before the orientation" to heic(quirks = Quirks(exifExtentLength = 20)),
            "an iloc field of nine bytes" to heic(quirks = Quirks(ilocOffsetSize = 9)),
        )
        for ((name, built) in damaged) assertEquals(name, HeicTurn.DAMAGED, refusal(built.bytes))
    }

    @Test
    fun `whole turns change nothing, with or without irot`() {
        for (turns in listOf(0, 4, -4, 8)) {
            assertEquals(HeicTurnResult.Unchanged, HeicTurn.turn(heic().bytes, turns))
            assertEquals(HeicTurnResult.Unchanged, HeicTurn.turn(heic(primaryIrot = null).bytes, turns))
        }
    }

    @Test
    fun `the input bytes are not changed`() {
        val built = heic()
        val before = built.bytes.copyOf()

        HeicTurn.turn(built.bytes, 1)

        assertArrayEquals(before, built.bytes)
    }

    // --- what is refused

    @Test
    fun `a file that is not a HEIC is refused`() {
        assertEquals(HeicTurn.NOT_A_HEIC, refusal(jpegWith()))
        assertEquals(HeicTurn.NOT_A_HEIC, refusal(ByteArray(0)))
        assertEquals(HeicTurn.NOT_A_HEIC, refusal(heicBox("ftyp", "heic".toByteArray() + int32(0))))
    }

    @Test
    fun `a mirrored photo is refused, since a turn would have to be combined with the mirror`() {
        assertEquals(HeicTurn.MIRRORED, refusal(heic(mirror = true).bytes))
    }

    @Test
    fun `two irot properties on one item, or a property that does not exist, are damage`() {
        assertEquals(HeicTurn.DAMAGED, refusal(heic(twoPrimaryIrot = true).bytes))
        assertEquals(HeicTurn.DAMAGED, refusal(heic(badIpmaIndex = true).bytes))
    }

    @Test
    fun `an Exif that points past the end of the file is damage`() {
        val built = heic(metaFirst = true).bytes

        assertEquals(HeicTurn.DAMAGED, refusal(built.copyOf(built.size - 10)))
    }

    @Test
    fun `a box smaller than its own header is damage`() {
        val built = heic().bytes.copyOf()
        built[3] = 4 // the size of ftyp

        assertEquals(HeicTurn.DAMAGED, refusal(built))
    }

    @Test
    fun `a file without its primary item, properties or locations is damage`() {
        for (box in listOf("pitm", "iprp", "iloc", "ipco", "iinf")) {
            val bytes = heic().bytes
            val at = bytes.indices.first { bytes.copyOfRange(it, it + 4).contentEquals(box.toByteArray()) }
            "xxxx".toByteArray().copyInto(bytes, at)
            assertEquals(box, HeicTurn.DAMAGED, refusal(bytes))
        }
    }

    @Test
    fun `any prefix of a HEIC is refused and never throws`() {
        for (built in listOf(heic().bytes, heic(metaFirst = true).bytes, heic(largeMdat = true).bytes)) {
            for (cut in built.indices) {
                val result = HeicTurn.turn(built.copyOf(cut), 1)
                assertTrue("cut at $cut: $result", result is HeicTurnResult.Refused)
            }
        }
    }
}
