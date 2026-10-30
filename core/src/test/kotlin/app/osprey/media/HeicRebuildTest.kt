package app.osprey.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val EXIF_OF_IROT = mapOf(0 to 1, 1 to 8, 2 to 3, 3 to 6)
private const val IROT_BOX_SIZE = 9
private val IROT_TYPE = "irot".toByteArray()
private const val FIRST_TILE_AT = 32 // the ftyp box (24 bytes) and the mdat header (8)

class HeicRebuildTest {

    private fun rebuilt(file: ByteArray, quarterTurns: Int): HeicTurnResult.Rebuilt =
        when (val result = HeicTurn.turn(file, quarterTurns)) {
            is HeicTurnResult.Rebuilt -> result
            else -> throw AssertionError("expected a rebuilt file, got $result")
        }

    private fun refusal(file: ByteArray, quarterTurns: Int = 1): String =
        when (val result = HeicTurn.turn(file, quarterTurns)) {
            is HeicTurnResult.Refused -> result.reason
            else -> throw AssertionError("expected a refusal, got $result")
        }

    /** Where the irot boxes are: each starts four bytes before its type, with the size 9 in front of it. */
    private fun irotBoxes(bytes: ByteArray): List<Int> = (4 until bytes.size - IROT_TYPE.size).filter { at ->
        bytes.copyOfRange(at, at + IROT_TYPE.size).contentEquals(IROT_TYPE) && readInt(bytes, at - 4) == IROT_BOX_SIZE
    }.map { it - 4 }

    /** The sizes of the top-level boxes: they must add up to the file, with `meta` exactly as long as it says. */
    private fun topLevel(bytes: ByteArray): List<Pair<String, Int>> {
        val boxes = mutableListOf<Pair<String, Int>>()
        var at = 0
        while (at < bytes.size) {
            val size = readInt(bytes, at)
            boxes += String(bytes, at + 4, 4) to size
            at += size
        }
        assertEquals(bytes.size, at)
        return boxes
    }

    private fun exifValue(bytes: ByteArray, at: Int, bigEndian: Boolean): Int =
        if (bigEndian) (bytes[at].toInt() and 0xFF shl 8) or (bytes[at + 1].toInt() and 0xFF)
        else (bytes[at + 1].toInt() and 0xFF shl 8) or (bytes[at].toInt() and 0xFF)

    private fun angleAfter(turns: Int) = (0 - turns).mod(4)

    // --- what is built

    @Test
    fun `a photo with no irot gets one, set for the turn, and every other byte of the file is kept`() {
        for (turns in listOf(1, 2, 3, 5, -1, -2, -3)) {
            val built = heic(primaryIrot = null, exifOrientation = 1)
            val result = rebuilt(built.bytes, turns)

            assertEquals(built.bytes.size + IROT_BOX_SIZE + 1, result.bytes.size)
            val boxes = irotBoxes(result.bytes)
            assertEquals(1, boxes.size)
            assertEquals("turns $turns", angleAfter(turns), result.bytes[boxes.single() + IROT_BOX_SIZE - 1].toInt())
            assertEquals(listOf("ftyp", "mdat", "meta"), topLevel(result.bytes).map { it.first })
            // the tiles and the Exif sit in mdat, before meta: untouched but for the Exif orientation value
            val beforeMeta = topLevel(result.bytes).takeWhile { it.first != "meta" }.sumOf { it.second }
            val valueAt = built.exifValueAt!!
            val expected = built.bytes.copyOfRange(0, beforeMeta)
            result.bytes.copyInto(expected, valueAt, valueAt, valueAt + 2)
            assertArrayEquals(expected, result.bytes.copyOfRange(0, beforeMeta))
        }
    }

    @Test
    fun `the Exif orientation is written to match, in either byte order`() {
        for (bigEndian in listOf(false, true)) {
            for (turns in 1..3) {
                val built = heic(primaryIrot = null, exifOrientation = 1, exifBigEndian = bigEndian)

                val out = rebuilt(built.bytes, turns).bytes

                assertEquals(EXIF_OF_IROT.getValue(angleAfter(turns)), exifValue(out, built.exifValueAt!!, bigEndian))
            }
        }
    }

    @Test
    fun `the new file is a HEIC that turns again, to the angle the first turn implies`() {
        val built = heic(primaryIrot = null, exifOrientation = 1)
        val out = rebuilt(built.bytes, 1).bytes
        val again = HeicTurn.turn(out, 1) as HeicTurnResult.Patched

        val turned = out.copyOf().also { bytes -> again.edits.forEach { it.bytes.copyInto(bytes, it.offset) } }

        val at = irotBoxes(out).single() + IROT_BOX_SIZE - 1
        assertEquals(angleAfter(2), turned[at].toInt())
    }

    @Test
    fun `no Exif item means nothing to say, and an Exif without an orientation entry says so`() {
        assertNull(rebuilt(heic(primaryIrot = null, exif = false).bytes, 1).note)
        assertEquals(HeicTurn.EXIF_NOTE, rebuilt(heic(primaryIrot = null, exifOrientation = null).bytes, 1).note)
        assertNull(rebuilt(heic(primaryIrot = null, exifOrientation = 1).bytes, 1).note)
    }

    @Test
    fun `an irot on a tile does not count for the primary item`() {
        val built = heic(primaryIrot = null, tileIrot = 1, exifOrientation = 1)

        val out = rebuilt(built.bytes, 1).bytes

        assertEquals(2, irotBoxes(out).size)
    }

    @Test
    fun `the same is built whatever the layout of the file`() {
        val layouts = mapOf(
            "ipma 2-byte associations" to (heic(primaryIrot = null, ipmaFlags = 1) to 2),
            "ipma version 1" to (heic(primaryIrot = null, ipmaVersion = 1) to 1),
            "iloc version 0" to (heic(primaryIrot = null, ilocVersion = 0) to 1),
            "iloc version 2" to (heic(primaryIrot = null, ilocVersion = 2) to 1),
            "64-bit mdat size" to (heic(primaryIrot = null, largeMdat = true) to 1),
            "boxes after meta" to (heic(primaryIrot = null, trailer = heicBox("free", ByteArray(40)) + heicBox("sefd", ByteArray(9))) to 1),
        )
        for ((name, pair) in layouts) {
            val (built, associationBytes) = pair
            val result = rebuilt(built.bytes, 1)
            assertEquals(name, built.bytes.size + IROT_BOX_SIZE + associationBytes, result.bytes.size)
            assertEquals(name, 1, irotBoxes(result.bytes).size)
            assertEquals(name, EXIF_OF_IROT.getValue(angleAfter(1)), exifValue(result.bytes, built.exifValueAt!!, false))
        }
    }

    @Test
    fun `an Exif inside meta moves with it and is still written`() {
        val built = heic(primaryIrot = null, exifOrientation = 1, exifInIdat = true)

        val out = rebuilt(built.bytes, 1).bytes

        // the new box and the association are both inserted before the idat
        assertEquals(EXIF_OF_IROT.getValue(angleAfter(1)), exifValue(out, built.exifValueAt!! + IROT_BOX_SIZE + 1, false))
    }

    @Test
    fun `whole turns change nothing and the input is not modified`() {
        val built = heic(primaryIrot = null)
        val before = built.bytes.copyOf()

        assertEquals(HeicTurnResult.Unchanged, HeicTurn.turn(built.bytes, 4))
        rebuilt(built.bytes, 1)

        assertArrayEquals(before, built.bytes)
    }

    // --- where it is not safe

    @Test
    fun `a layout that would have to move data, or that cannot be resized, is refused and nothing is built`() {
        val unsafe = mapOf(
            "meta before mdat" to heic(primaryIrot = null, metaFirst = true),
            "a meta box with a 64-bit size" to heic(primaryIrot = null, quirks = Quirks(largeMeta = true)),
            "a primary item with no associations" to heic(primaryIrot = null, quirks = Quirks(primaryHasNoAssociations = true)),
            "more properties than a one-byte index holds" to heic(primaryIrot = null, quirks = Quirks(extraProps = 125)),
            "an item stored in a way we cannot follow" to heic(primaryIrot = null, quirks = Quirks(tileMethod = 2)),
            "no item data to check" to heic(primaryIrot = null, exif = false, quirks = Quirks(ilocRows = emptyList())),
        )
        for ((name, built) in unsafe) assertEquals(name, HeicTurn.UNSAFE_LAYOUT, refusal(built.bytes))
    }

    @Test
    fun `a rebuilt file the check does not accept is never returned`() {
        val built = heic(primaryIrot = null)

        val result = HeicTurn.turn(built.bytes, 1) { _, _, _ -> false }

        assertEquals(HeicTurnResult.Refused(HeicTurn.UNSAFE_LAYOUT), result)
    }

    @Test
    fun `the last index a one-byte association holds is still used`() {
        val built = heic(primaryIrot = null, quirks = Quirks(extraProps = 124))

        assertEquals(1, irotBoxes(rebuilt(built.bytes, 1).bytes).size)
    }

    @Test
    fun `the new association is essential, narrow and wide`() {
        val narrow = heic(primaryIrot = null)
        val wide = heic(primaryIrot = null, ipmaFlags = 1)
        val essentialIndex = 0x80 or 3 // ispe and hvcC come first: the new irot is property 3

        assertEquals(count(narrow.bytes, essentialIndex) + 1, count(rebuilt(narrow.bytes, 1).bytes, essentialIndex))
        assertEquals(countPair(wide.bytes, 0x80, 3) + 1, countPair(rebuilt(wide.bytes, 1).bytes, 0x80, 3))
    }

    private fun count(bytes: ByteArray, value: Int) = bytes.count { it.toInt() and 0xFF == value }

    private fun countPair(bytes: ByteArray, first: Int, second: Int) =
        (0 until bytes.size - 1).count { bytes[it].toInt() and 0xFF == first && bytes[it + 1].toInt() == second }

    @Test
    fun `two-byte associations hold the same many properties without trouble`() {
        val built = heic(primaryIrot = null, ipmaFlags = 1, quirks = Quirks(extraProps = 125))

        assertEquals(1, irotBoxes(rebuilt(built.bytes, 1).bytes).size)
    }

    // --- the check that makes it safe

    @Test
    fun `the check accepts what was built and nothing else`() {
        val built = heic(primaryIrot = null, exifOrientation = 1)
        val good = rebuilt(built.bytes, 1).bytes
        val at = irotBoxes(good).single() + IROT_BOX_SIZE - 1
        val angle = angleAfter(1)

        assertTrue(HeicRebuild.isSound(built.bytes, good, angle))

        assertFalse("the old file", HeicRebuild.isSound(built.bytes, built.bytes, angle))
        assertFalse("garbage", HeicRebuild.isSound(built.bytes, ByteArray(40), angle))
        assertFalse("the wrong angle", HeicRebuild.isSound(built.bytes, good, (angle + 1) % 4))
        assertFalse("a byte too many", HeicRebuild.isSound(built.bytes, good + byteArrayOf(0), angle))
        assertFalse("the wrong Exif", HeicRebuild.isSound(built.bytes, good.copyOf().also { it[built.exifValueAt!!] = 1 }, angle))
        assertFalse("a tile that changed", HeicRebuild.isSound(built.bytes, good.copyOf().also { it[FIRST_TILE_AT] = 7 }, angle))
        val fewerItems = heic(primaryIrot = null, exifOrientation = 1, quirks = Quirks(ilocRows = listOf(2, 4))).bytes
        assertFalse("a different item table", HeicRebuild.isSound(fewerItems, good, angle))
        val noOrientation = rebuilt(heic(primaryIrot = null, exifOrientation = null).bytes, 1).bytes
        assertFalse("an Exif that lost its orientation", HeicRebuild.isSound(built.bytes, noOrientation, angle))
        val unfollowable = Quirks(tileMethod = 2)
        val oldUnfollowable = heic(primaryIrot = null, exifOrientation = 1, quirks = unfollowable).bytes
        assertFalse("data we cannot follow in the old file", HeicRebuild.isSound(oldUnfollowable, good, angle))
        val newUnfollowable = heic(primaryIrot = angle, exifOrientation = EXIF_OF_IROT.getValue(angle), quirks = unfollowable)
        assertFalse("data we cannot follow in the new file", HeicRebuild.isSound(built.bytes, newUnfollowable.bytes, angle))
        val plain = heic(primaryIrot = null, exif = false).bytes
        val plainTurned = rebuilt(plain, 1).bytes
        assertFalse("the wrong angle, with no Exif to give it away", HeicRebuild.isSound(plain, plainTurned, (angle + 1) % 4))
        val oneTile = heic(primaryIrot = null, exif = false, quirks = Quirks(ilocRows = listOf(2))).bytes
        assertFalse("fewer items before", HeicRebuild.isSound(oneTile, plainTurned, angle))
        assertTrue("sanity: the angle byte is where the box says", good[at].toInt() == angle)
    }
}
