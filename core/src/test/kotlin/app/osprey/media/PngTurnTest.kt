package app.osprey.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val FORMATS = listOf(
    GRAY to 1, GRAY to 2, GRAY to 4, GRAY to 8, GRAY to 16, RGB to 8, RGB to 16,
    PALETTE to 1, PALETTE to 2, PALETTE to 4, PALETTE to 8, GRAY_ALPHA to 8, GRAY_ALPHA to 16, RGBA to 8, RGBA to 16,
)
private val TURNS = listOf(1, 2, 3, -1, -2, -3, 5)
private const val GRADIENT_SIDE = 64
private const val PHYS_X = 2835
private const val PHYS_Y = 5670
private const val FAR = 30000
private const val RAW_OF_7_BY_5_RGB8 = 5 * (7 * 3 + 1)

class PngTurnTest {

    private fun image(colorType: Int = RGB, depth: Int = 8, width: Int = 7, height: Int = 5) =
        randomSamples(width, height, depth, colorType)

    private fun turned(png: ByteArray, quarterTurns: Int): ByteArray = when (val result = PngTurn.turn(png, quarterTurns)) {
        is PngTurnResult.Turned -> result.bytes
        is PngTurnResult.Refused -> throw AssertionError("refused: ${result.reason}")
    }

    private fun refusal(png: ByteArray, quarterTurns: Int = 1, limit: Int = PngTurn.MAX_RAW_BYTES): String =
        when (val result = PngTurn.turn(png, quarterTurns, limit)) {
            is PngTurnResult.Turned -> throw AssertionError("turned")
            is PngTurnResult.Refused -> result.reason
        }

    private fun assertSame(expected: Samples, actual: Samples) = assertTrue(
        "expected ${expected.width}x${expected.height} depth ${expected.depth}, got ${actual.width}x${actual.height} " +
            "depth ${actual.depth}",
        expected.sameAs(actual),
    )

    private fun orientationIn(png: ByteArray): Int {
        val data = chunksOf(png).first { it.type == "eXIf" }.data
        return TiffOrientation.read(data, TiffOrientation.locate(data)!!).ordinal + 1
    }

    private fun physOf(png: ByteArray): Pair<Int, Int> {
        val data = chunksOf(png).first { it.type == "pHYs" }.data
        return readInt(data, 0) to readInt(data, 4)
    }

    private fun physChunk(x: Int, y: Int) = chunk("pHYs", int32(x) + int32(y) + byteArrayOf(1))

    // --- pixels

    @Test
    fun `a turn moves every pixel the way the EXIF table says`() {
        val source = image()
        for (turns in TURNS) {
            assertSame(source.oriented(ExifOrientation.NORMAL.turnedBy(turns)), decode(turned(pngOf(source), turns)))
        }
    }

    @Test
    fun `every colour type and bit depth turns without a pixel changing`() {
        for ((colorType, depth) in FORMATS) {
            val source = image(colorType, depth)
            val result = decode(turned(pngOf(source), 1))
            assertSame(source.oriented(ExifOrientation.ROTATE_90), result)
        }
    }

    @Test
    fun `every filter type is read`() {
        val source = image(RGB, 16, 9, 6)
        for (filter in 0..4) {
            assertSame(source.oriented(ExifOrientation.ROTATE_90), decode(turned(pngOf(source, filters = { filter }), 1)))
        }
    }

    @Test
    fun `image data split into many chunks is read`() {
        val source = image()
        assertSame(source.oriented(ExifOrientation.ROTATE_90), decode(turned(pngOf(source, idatSize = 3), 1)))
    }

    @Test
    fun `four quarter turns in a row give back the pixels`() {
        val source = image(RGBA, 16, 6, 9)
        val result = (1..4).fold(pngOf(source)) { png, _ -> turned(png, 1) }
        assertSame(source, decode(result))
    }

    @Test
    fun `whole turns leave the file as it is`() {
        val png = pngOf(image(), before = listOf(physChunk(PHYS_X, PHYS_Y)))
        for (turns in listOf(0, 4, -4, 8)) assertArrayEquals(png, turned(png, turns))
    }

    @Test
    fun `the input bytes are not changed`() {
        val png = pngOf(image())
        val before = png.copyOf()
        turned(png, 1)
        assertArrayEquals(before, png)
    }

    @Test
    fun `the output is a plain PNG header with the sides swapped on an odd turn`() {
        val header = chunksOf(turned(pngOf(image(width = 7, height = 5), idatSize = 4), 1)).first().data
        assertEquals(5, readInt(header, 0))
        assertEquals(7, readInt(header, 4))
        assertArrayEquals(byteArrayOf(8, RGB.toByte(), 0, 0, 0), header.copyOfRange(8, 13))
        val half = chunksOf(turned(pngOf(image(width = 7, height = 5)), 2)).first().data
        assertEquals(7, readInt(half, 0))
    }

    @Test
    fun `rows are filtered so a smooth picture does not grow`() {
        val values = IntArray(GRADIENT_SIDE * GRADIENT_SIDE * 3) { (it / 3 % GRADIENT_SIDE) * 3 + it / 3 / GRADIENT_SIDE }
        val smooth = Samples(GRADIENT_SIDE, GRADIENT_SIDE, 8, RGB, values)
        val result = turned(pngOf(smooth), 1)
        val unfiltered = pngOf(decode(result), filters = { 0 }, idatSize = Int.MAX_VALUE)
        assertTrue("${result.size} vs ${unfiltered.size}", result.size < unfiltered.size)
    }

    // --- chunks

    @Test
    fun `every other chunk is kept byte for byte in its place and the image data becomes one chunk`() {
        val before = listOf(
            chunk("gAMA", int32(45455)), chunk("iCCP", "p\u0000\u0000xx".toByteArray()), chunk("tEXt", "k\u0000v".toByteArray()),
            chunk("iTXt", "i\u0000\u0000\u0000\u0000\u0000ü".toByteArray()), chunk("tIME", ByteArray(7) { it.toByte() }),
            chunk("zzZz", byteArrayOf(1, 2, 3)),
        )
        val after = listOf(chunk("tEXt", "late\u0000text".toByteArray()))
        val source = pngOf(image(), before = before, after = after, idatSize = 5)

        val result = chunksOf(turned(source, 1))

        assertEquals(listOf("IHDR") + before.map { String(it, 4, 4) } + "IDAT" + "tEXt" + "IEND", result.map { it.type })
        val kept = (before + after).map { it.toList() }
        assertEquals(kept, result.filter { it.type !in setOf("IHDR", "IDAT", "IEND") }.map { it.bytes.toList() })
        assertTrue(result.all { it.crcOk })
    }

    @Test
    fun `a palette and its transparency stay as they were`() {
        val trns = chunk("tRNS", byteArrayOf(0, 40, 80))
        val source = pngOf(image(PALETTE, 2), before = listOf(trns))
        val result = chunksOf(turned(source, 1))
        assertArrayEquals(plte(2), result.first { it.type == "PLTE" }.bytes)
        assertArrayEquals(trns, result.first { it.type == "tRNS" }.bytes)
    }

    @Test
    fun `the physical size swaps its two axes when the sides swap`() {
        val source = pngOf(image(), before = listOf(physChunk(PHYS_X, PHYS_Y)))
        assertEquals(PHYS_Y to PHYS_X, physOf(turned(source, 1)))
        assertEquals(PHYS_Y to PHYS_X, physOf(turned(source, 3)))
        assertEquals(PHYS_X to PHYS_Y, physOf(turned(source, 2)))
        assertTrue(chunksOf(turned(source, 1)).first { it.type == "pHYs" }.crcOk)
    }

    @Test
    fun `the unit byte of the physical size is kept`() {
        val unit = chunksOf(turned(pngOf(image(), before = listOf(physChunk(1, 2))), 1)).first { it.type == "pHYs" }.data[8]
        assertEquals(1.toByte(), unit)
    }

    @Test
    fun `an upright EXIF chunk stays as it is`() {
        val exif = exifChunk(1)
        val result = chunksOf(turned(pngOf(image(), before = listOf(exif)), 1))
        assertArrayEquals(exif, result.first { it.type == "eXIf" }.bytes)
    }

    @Test
    fun `a stored EXIF orientation is baked into the pixels and reset to upright`() {
        for (bigEndian in listOf(false, true)) {
            for (stored in 1..8) {
                val source = image()
                val exif = exifChunk(stored, bigEndian)
                val result = turned(pngOf(source, before = listOf(exif)), 1)

                val expected = source.oriented(ExifOrientation.fromExif(stored).turnedBy(1))
                assertSame(expected, decode(result))
                assertEquals(1, orientationIn(result))
                val chunk = chunksOf(result).first { it.type == "eXIf" }
                assertTrue(chunk.crcOk)
                assertEquals(exif.size, chunk.bytes.size)
            }
        }
    }

    @Test
    fun `only the orientation value changes inside the EXIF chunk`() {
        val exif = exifChunk(6)
        val data = chunksOf(turned(pngOf(image(), before = listOf(exif)), 1)).first { it.type == "eXIf" }.data
        val original = exif.copyOfRange(8, exif.size - 4) // the chunk's data: after length and type, before the CRC
        assertEquals(1, original.indices.count { original[it] != data[it] })
    }

    @Test
    fun `the sides swap by the final orientation, not by the turn`() {
        val source = pngOf(image(), before = listOf(exifChunk(6), physChunk(PHYS_X, PHYS_Y)))
        assertEquals(PHYS_X to PHYS_Y, physOf(turned(source, 1)))
    }

    @Test
    fun `an EXIF chunk without an orientation is kept and the file is turned by the turn alone`() {
        val exif = exifChunk(null)
        val source = image()
        val result = turned(pngOf(source, before = listOf(exif)), 1)
        assertSame(source.oriented(ExifOrientation.ROTATE_90), decode(result))
        assertArrayEquals(exif, chunksOf(result).first { it.type == "eXIf" }.bytes)
    }

    @Test
    fun `a file without an EXIF chunk gets none`() {
        assertFalse(chunksOf(turned(pngOf(image()), 1)).any { it.type == "eXIf" })
    }

    @Test
    fun `a chunk this code does not know is carried when it is safe to copy`() {
        val unknown = chunk("zzZz", byteArrayOf(9))
        assertTrue(chunksOf(turned(pngOf(image(), before = listOf(unknown)), 1)).any { it.bytes.contentEquals(unknown) })
    }

    // --- refusals

    @Test
    fun `an unknown chunk that is not safe to copy is refused`() {
        assertEquals(PngTurn.UNKNOWN_CHUNK, refusal(pngOf(image(), before = listOf(chunk("zzZZ", byteArrayOf(9))))))
    }

    @Test
    fun `an unknown critical chunk is refused`() {
        assertEquals(PngTurn.UNKNOWN_CHUNK, refusal(pngOf(image(), before = listOf(chunk("ABCD", byteArrayOf(9))))))
    }

    @Test
    fun `an interlaced PNG is refused`() {
        assertEquals(PngTurn.INTERLACED, refusal(pngOf(image(), interlace = 1)))
    }

    @Test
    fun `an animated PNG is refused`() {
        val control = chunk("acTL", int32(2) + int32(0))
        assertEquals(PngTurn.ANIMATED, refusal(pngOf(image(), before = listOf(control))))
    }

    @Test
    fun `a picture over the limit is refused before its data is read`() {
        val huge = rawPng(chunk("IHDR", ihdrData(FAR, FAR, 8, RGB)), chunk("IDAT", byteArrayOf(1, 2)), chunk("IEND"))
        assertEquals(PngTurn.TOO_BIG, refusal(huge))
    }

    @Test
    fun `the limit counts the bytes of the scanlines and is inclusive`() {
        val png = pngOf(image())
        assertTrue(PngTurn.turn(png, 1, RAW_OF_7_BY_5_RGB8) is PngTurnResult.Turned)
        assertEquals(PngTurn.TOO_BIG, refusal(png, limit = RAW_OF_7_BY_5_RGB8 - 1))
    }

    @Test
    fun `bytes after the end chunk are refused`() {
        assertEquals(PngTurn.TRAILING_DATA, refusal(pngOf(image()) + byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `anything that is not a whole PNG is refused and never throws`() {
        val png = pngOf(image(), before = listOf(physChunk(1, 2)))
        for (cut in png.indices) assertEquals("cut at $cut", PngTurn.DAMAGED, refusal(png.copyOf(cut)))
        assertEquals(PngTurn.DAMAGED, refusal(ByteArray(0)))
        assertEquals(PngTurn.DAMAGED, refusal("GIF89a, not a PNG at all".toByteArray()))
    }

    @Test
    fun `a wrong checksum is refused wherever it is`() {
        val png = pngOf(image(), before = listOf(physChunk(1, 2)), idatSize = 1000)
        var at = 8
        while (at < png.size) {
            val damaged = png.copyOf().also { it[at + 8] = (it[at + 8] + 1).toByte() }
            if (readInt(png, at) > 0) assertEquals("chunk at $at", PngTurn.DAMAGED, refusal(damaged))
            at += 12 + readInt(png, at)
        }
    }

    @Test
    fun `the structure of the file is checked`() {
        val ihdr = chunk("IHDR", ihdrData(7, 5, 8, RGB))
        val whole = deflate(scanlines(image(), { 0 }))
        val idat = chunk("IDAT", whole)
        val cases = mapOf(
            "two headers" to rawPng(ihdr, ihdr, idat, chunk("IEND")),
            "an end chunk with data" to rawPng(ihdr, idat, chunk("IEND", byteArrayOf(1))),
            "interlace method that does not exist" to
                rawPng(chunk("IHDR", ihdrData(7, 5, 8, RGB, interlace = 2)), idat, chunk("IEND")),
            "a chunk type that is not letters" to rawPng(ihdr, chunk("ab1d"), idat, chunk("IEND")),
            "image data longer than the picture" to
                rawPng(ihdr, chunk("IDAT", deflate(ByteArray(RAW_OF_7_BY_5_RGB8 + 50))), chunk("IEND")),
            "image data that asks for a dictionary" to
                rawPng(ihdr, chunk("IDAT", byteArrayOf(0x78, 0xBB.toByte(), 0, 0, 0, 1, 3, 0)), chunk("IEND")),
            "bytes after the end of the zlib stream" to
                rawPng(ihdr, chunk("IDAT", deflate(scanlines(image(), { 0 })) + ByteArray(3) { 1 }), chunk("IEND")),
            "a second image chunk after the stream is whole" to rawPng(ihdr, idat, idat, chunk("IEND")),
            "no image data" to rawPng(ihdr, chunk("IEND")),
            "no header" to rawPng(idat, chunk("IEND")),
            "gap in the image data" to rawPng(
                ihdr, chunk("IDAT", whole.copyOf(10)), chunk("tEXt", byteArrayOf(0)),
                chunk("IDAT", whole.copyOfRange(10, whole.size)), chunk("IEND"),
            ),
            "short header" to rawPng(chunk("IHDR", ihdrData(7, 5, 8, RGB).copyOf(12)), idat, chunk("IEND")),
            "zero width" to rawPng(chunk("IHDR", ihdrData(0, 5, 8, RGB)), idat, chunk("IEND")),
            "zero height" to rawPng(chunk("IHDR", ihdrData(7, 0, 8, RGB)), idat, chunk("IEND")),
            "depth not allowed for the colour type" to rawPng(
                chunk("IHDR", ihdrData(7, 5, 4, RGB)),
                chunk("IDAT", deflate(scanlines(randomSamples(7, 5, 4, RGB), { 0 }))), chunk("IEND"),
            ),
            "palette of 16 bits" to rawPng(chunk("IHDR", ihdrData(7, 5, 16, PALETTE)), idat, chunk("IEND")),
            "unknown colour type" to rawPng(chunk("IHDR", ihdrData(7, 5, 8, 5)), idat, chunk("IEND")),
            "unknown compression method" to rawPng(chunk("IHDR", ihdrData(7, 5, 8, RGB).also { it[10] = 1 }), idat, chunk("IEND")),
            "unknown filter method" to rawPng(chunk("IHDR", ihdrData(7, 5, 8, RGB).also { it[11] = 1 }), idat, chunk("IEND")),
            "image data that is not zlib" to rawPng(ihdr, chunk("IDAT", ByteArray(40) { 7 }), chunk("IEND")),
            "image data of the wrong length" to rawPng(ihdr, chunk("IDAT", deflate(ByteArray(3))), chunk("IEND")),
            "image data cut short" to rawPng(ihdr, chunk("IDAT", deflate(scanlines(image(), { 0 })).copyOf(10)), chunk("IEND")),
            "a filter type that does not exist" to rawPng(ihdr, chunk("IDAT", deflate(scanlines(image(), { 7 }))), chunk("IEND")),
            "the first filter type that does not exist" to
                rawPng(ihdr, chunk("IDAT", deflate(scanlines(image(), { 5 }))), chunk("IEND")),
            "palette image without a palette" to rawPng(
                chunk("IHDR", ihdrData(7, 5, 8, PALETTE)), chunk("IDAT", deflate(scanlines(image(PALETTE, 8), { 0 }))), chunk("IEND"),
            ),
        )
        for ((name, png) in cases) assertEquals(name, PngTurn.DAMAGED, refusal(png))
    }

    @Test
    fun `a chunk length that cannot be right is refused`() {
        val png = PNG_SIGNATURE + int32(-1) + "IHDR".toByteArray() + ByteArray(20)
        assertEquals(PngTurn.DAMAGED, refusal(png))
    }

    @Test
    fun `a physical size that is not nine bytes is kept as it is`() {
        val odd = chunk("pHYs", byteArrayOf(1, 2, 3))
        val result = chunksOf(turned(pngOf(image(), before = listOf(odd)), 1))
        assertArrayEquals(odd, result.first { it.type == "pHYs" }.bytes)
    }

    @Test
    fun `the paeth filter takes the left byte when it ties with the upper left one`() {
        val source = Samples(2, 2, 8, GRAY, intArrayOf(2, 1, 4, 9))
        assertSame(source.oriented(ExifOrientation.ROTATE_90), decode(turned(pngOf(source, filters = { 4 }), 1)))
    }
}
