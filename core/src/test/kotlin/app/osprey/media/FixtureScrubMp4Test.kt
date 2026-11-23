package app.osprey.media

import app.osprey.fixtures.Photos
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MEGABYTE = 1024 * 1024
private const val SECONDS_FROM_1904_TO_1970 = 2_082_844_800L
private const val ENCODER = "©too"

/**
 * The guard on the MP4 fixtures (#131). A clip keeps the camera's own name and its container's creation time, since
 * for a video those are what the tests compare (#84); the rest of the file must carry nothing personal: no location,
 * no device, no more than a video and an audio track, and a body small enough not to slow every CI run.
 */
class FixtureScrubMp4Test {

    private fun u32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun u64(v: Long) = u32((v shr 32).toInt()) + u32(v.toInt())

    private fun atom(type: String, vararg parts: ByteArray): ByteArray {
        val payload = parts.fold(ByteArray(0)) { all, one -> all + one }
        return u32(payload.size + 8) + type.toByteArray(Charsets.ISO_8859_1) + payload
    }

    private fun mvhd(created: Instant? = null, version: Int = 0): ByteArray {
        val seconds = created?.let { it.epochSecond + SECONDS_FROM_1904_TO_1970 } ?: 0L
        val times = if (version == 1) u64(seconds) + u64(seconds) else u32(seconds.toInt()) + u32(seconds.toInt())
        return atom("mvhd", byteArrayOf(version.toByte(), 0, 0, 0), times, ByteArray(MVHD_REST))
    }

    private fun track(handler: String = "vide", vararg extra: ByteArray) = atom(
        "trak",
        atom("tkhd"),
        atom("edts"),
        atom("mdia", atom("mdhd"), atom("hdlr", ByteArray(8), handler.toByteArray(), ByteArray(12)), atom("minf")),
        *extra,
    )

    private fun encoderTag(text: String = "Lavf62.12.101") = atom(
        "udta",
        atom(
            "meta",
            ByteArray(4),
            atom("hdlr", ByteArray(8), "mdir".toByteArray()),
            atom("ilst", atom(ENCODER, atom("data", byteArrayOf(0, 0, 0, 1), ByteArray(4), text.toByteArray()))),
        ),
    )

    private fun clip(
        vararg moov: ByteArray,
        top: ByteArray = ByteArray(0),
        body: Int = 64,
    ) = atom("ftyp", "isom".toByteArray(), ByteArray(4)) + top + atom("free") + atom("mdat", ByteArray(body)) +
        atom("moov", *moov)

    private val clean get() = clip(mvhd(), track("vide"), track("soun"), encoderTag())

    @Test
    fun `a clip with a video and an audio track, an encoder tag and a plain body has nothing to report`() {
        assertEquals(emptyList<String>(), Mp4Scrub.problems(clean))
        assertEquals(emptyList<String>(), Mp4Scrub.problems(clip(mvhd(), track("vide"), track("soun"))))
        assertEquals(emptyList<String>(), Mp4Scrub.problems(clip(mvhd(), track("vide"))))
    }

    @Test
    fun `a WhatsApp beam atom of a few numbers is kept, a larger one is reported`() {
        val small = clip(mvhd(), track("vide"), top = atom("beam", ByteArray(16)))
        val large = clip(mvhd(), track("vide"), top = atom("beam", ByteArray(64)))

        assertEquals(emptyList<String>(), Mp4Scrub.problems(small))
        assertTrue(Mp4Scrub.problems(large).toString(), Mp4Scrub.problems(large).any { "beam" in it })
    }

    @Test
    fun `a top-level atom a clip must not carry is reported by name`() {
        val problems = Mp4Scrub.problems(clip(mvhd(), track("vide"), top = atom("uuid", ByteArray(16)) + atom("XMP_")))

        assertTrue(problems.toString(), problems.any { "uuid" in it && "XMP_" in it })
    }

    @Test
    fun `a location or another tag in the user data is reported`() {
        val gps = atom("udta", atom("meta", ByteArray(4), atom("ilst", atom("©xyz", ByteArray(8)))))
        val loci = atom("udta", atom("loci", ByteArray(12)))
        val both = atom(
            "udta",
            atom("meta", ByteArray(4), atom("ilst", atom(ENCODER, ByteArray(8)), atom("©mod", ByteArray(8)))),
        )
        val long = encoderTag("x".repeat(200))

        for (udta in listOf(gps, loci, both, long)) {
            val problems = Mp4Scrub.problems(clip(mvhd(), track("vide"), udta))
            assertTrue(problems.toString(), problems.any { "udta" in it })
        }
    }

    @Test
    fun `a meta atom or a location atom straight in moov is reported`() {
        val problems = Mp4Scrub.problems(clip(mvhd(), track("vide"), atom("meta", ByteArray(4)), atom("loci")))

        assertTrue(problems.toString(), problems.any { "moov" in it && "meta" in it && "loci" in it })
    }

    @Test
    fun `an atom a track must not carry is reported`() {
        val problems = Mp4Scrub.problems(clip(mvhd(), track("vide", atom("udta"))))

        assertTrue(problems.toString(), problems.any { "trak" in it && "udta" in it })
    }

    @Test
    fun `an atom the media of a track must not carry is reported`() {
        val inMedia = atom(
            "trak",
            atom("tkhd"),
            atom("mdia", atom("hdlr", ByteArray(8), "vide".toByteArray(), ByteArray(12)), atom("udta")),
        )

        val problems = Mp4Scrub.problems(clip(mvhd(), inMedia))

        assertTrue(problems.toString(), problems.any { "mdia" in it && "udta" in it })
    }

    @Test
    fun `more than a video and an audio track is reported`() {
        val three = Mp4Scrub.problems(clip(mvhd(), track("vide"), track("soun"), track("soun")))
        val twoVideos = Mp4Scrub.problems(clip(mvhd(), track("vide"), track("vide")))
        val subtitles = Mp4Scrub.problems(clip(mvhd(), track("vide"), track("text")))

        assertTrue(three.toString(), three.any { "3 tracks" in it })
        assertTrue(twoVideos.toString(), twoVideos.any { "vide" in it })
        assertTrue(subtitles.toString(), subtitles.any { "text" in it })
    }

    @Test
    fun `a body larger than 16 MB is reported`() {
        val problems = Mp4Scrub.problems(clip(mvhd(), track("vide"), body = 16 * MEGABYTE + 1))

        assertTrue(problems.toString(), problems.any { "16 MB" in it })
        assertEquals(emptyList<String>(), Mp4Scrub.problems(clip(mvhd(), track("vide"), body = 15 * MEGABYTE)))
    }

    @Test
    fun `a file that is not a readable MP4 is reported, not thrown`() {
        assertTrue(Mp4Scrub.problems(ByteArray(40)).any { "MP4" in it })
        assertTrue(Mp4Scrub.problems(clean.copyOf(clean.size - 5)).any { "MP4" in it })
        assertTrue(Mp4Scrub.problems(ByteArray(3)).any { "MP4" in it })
        assertTrue(Mp4Scrub.problems(atom("free") + atom("mdat")).any { "ftyp" in it })
        assertTrue(Mp4Scrub.problems(atom("ftyp", ByteArray(8)) + atom("mdat")).any { "moov" in it })
    }

    @Test
    fun `an atom whose size field runs past the end or points backwards is reported`() {
        val backwards = u32(4) + "free".toByteArray() + clean

        assertTrue(Mp4Scrub.problems(backwards).any { "MP4" in it })
        assertTrue(Mp4Scrub.problems(u32(Int.MAX_VALUE) + "mdat".toByteArray()).any { "MP4" in it })
    }

    @Test
    fun `a device or GPS text in the index is reported by FixtureScrub, and a camera name is fine for a video`() {
        val device = clip(mvhd(), track("vide"), atom("udta", "Samsung SM-S918B".toByteArray()))

        val problems = FixtureScrub.problems("20190629_151754.mp4", device)

        assertTrue(problems.toString(), problems.any { "device" in it })
        assertEquals(emptyList<String>(), FixtureScrub.problems("20190629_151754.mp4", clean))
        assertEquals(emptyList<String>(), FixtureScrub.problems("VID-20180314-WA0073.MP4", clean))
        assertTrue(FixtureScrub.problems("20190629_151754.jpg", ByteArray(4)).any { "name" in it })
    }

    @Test
    fun `the creation time is read from mvhd, version 0 or 1, and none when it is zero`() {
        val time = Instant.parse("2019-06-29T13:18:11Z")

        assertEquals(time, Mp4Scrub.creationTime(clip(mvhd(time), track("vide"))))
        assertEquals(time, Mp4Scrub.creationTime(clip(mvhd(time, version = 1), track("vide"))))
        assertNull(Mp4Scrub.creationTime(clip(mvhd(), track("vide"))))
        assertNull(Mp4Scrub.creationTime(ByteArray(40)))
    }

    @Test
    fun `the moov text is what the leak scan reads`() {
        assertTrue("Lavf" in Mp4Scrub.moovText(clean))
        assertEquals("", Mp4Scrub.moovText(ByteArray(40)))
    }

    private val expected = mapOf(
        "20190629_151754.mp4" to "2019-06-29T13:18:11Z",
        "20190629_151754_1.mp4" to "2019-06-29T13:18:11Z",
        "20190629_153748.mp4" to "2019-06-29T13:37:59Z",
        "MOV_0057 (2).mp4" to "2018-12-02T09:37:53Z",
        "MOV_0057__flat1.mp4" to "2018-12-02T09:37:53Z",
        "MOV_0058.mp4" to "2018-12-02T09:37:57Z",
        "VID-20180314-WA0073.mp4" to null,
    )

    @Test
    fun `every real fixture video is scrubbed`() {
        val folder = Photos.need()
        val all = Photos.mp4(folder)
        assertTrue("no videos in the mp4 folder of $folder", all.isNotEmpty())

        val reported = all.associate {
            it.fileName.toString() to FixtureScrub.problems(it.fileName.toString(), Files.readAllBytes(it))
        }.filterValues { it.isNotEmpty() }

        assertEquals(emptyMap<String, List<String>>(), reported)
    }

    @Test
    fun `the real clips are the expected ones, each with its container time`() {
        val folder = Photos.need()
        val found = Photos.mp4(folder).associate {
            it.fileName.toString() to Mp4Scrub.creationTime(Files.readAllBytes(it))?.toString()
        }

        assertEquals(expected, found)
    }

    @Test
    fun `the clips the README lists are the clips in the folder`() {
        val folder = Photos.need()
        val readme = String(Files.readAllBytes(folder.resolve("README.md")), Charsets.UTF_8)
        val listed = Regex("`([^`]+\\.mp4)`").findAll(readme).map { it.groupValues[1] }.toSet()

        assertEquals(Photos.mp4(folder).map { it.fileName.toString() }.toSet(), listed)
    }

    private companion object {
        const val MVHD_REST = 80
    }
}
