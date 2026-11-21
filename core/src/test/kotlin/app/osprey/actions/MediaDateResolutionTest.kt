package app.osprey.actions

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.media.FakeMediaMetadataReader
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.utcToLocal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

private val ZONE: ZoneId = ZoneId.of("Europe/Paris")

// The container's UTC creation time; the reader hands it over converted to ZONE, as the platform readers do.
private val UTC: LocalDateTime = LocalDateTime.of(2020, 7, 10, 8, 0, 0)

class MediaDateResolutionTest {

    private fun resolve(name: String, source: MediaDateSource = MediaDateSource.VIDEO_METADATA): MediaDate? {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val entry = fs.addFile(fs.root, name)
        reader.setCaptureDateTime(entry, MediaDate(utcToLocal(UTC, ZONE), source))
        return resolveMediaDate(entry, name, reader, ZONE)
    }

    private fun assertNameWins(name: String, expected: LocalDateTime) =
        assertEquals(MediaDate(expected, MediaDateSource.FILENAME), resolve(name))

    private fun assertContainerWins(name: String) =
        assertEquals(MediaDate(utcToLocal(UTC, ZONE), MediaDateSource.VIDEO_METADATA), resolve(name))

    @Test
    fun `a name three hours ahead of the container instant wins`() =
        assertNameWins("VID_20200710_110000.mp4", LocalDateTime.of(2020, 7, 10, 11, 0, 0))

    @Test
    fun `a name five hours behind the container instant wins`() =
        assertNameWins("VID_20200710_030000.mp4", LocalDateTime.of(2020, 7, 10, 3, 0, 0))

    @Test
    fun `a name at the UTC reading itself wins`() =
        assertNameWins("VID_20200710_080000.mp4", LocalDateTime.of(2020, 7, 10, 8, 0, 0))

    @Test
    fun `a half-hour offset counts`() {
        assertNameWins("VID_20200710_133000.mp4", LocalDateTime.of(2020, 7, 10, 13, 30, 0))
        assertNameWins("VID_20200710_043000.mp4", LocalDateTime.of(2020, 7, 10, 4, 30, 0))
    }

    @Test
    fun `a 45-minute offset counts`() =
        assertNameWins("VID_20200710_134500.mp4", LocalDateTime.of(2020, 7, 10, 13, 45, 0))

    @Test
    fun `the extreme offsets plus 14 and minus 12 count`() {
        assertNameWins("VID_20200710_220000.mp4", LocalDateTime.of(2020, 7, 10, 22, 0, 0))
        assertNameWins("VID_20200709_200000.mp4", LocalDateTime.of(2020, 7, 9, 20, 0, 0))
    }

    @Test
    fun `an offset past either extreme keeps the container time`() {
        assertContainerWins("VID_20200710_221500.mp4")
        assertContainerWins("VID_20200709_194500.mp4")
    }

    @Test
    fun `a name within two minutes of an offset wins on both sides`() {
        assertNameWins("VID_20200710_110200.mp4", LocalDateTime.of(2020, 7, 10, 11, 2, 0))
        assertNameWins("VID_20200710_105800.mp4", LocalDateTime.of(2020, 7, 10, 10, 58, 0))
    }

    @Test
    fun `a name more than two minutes from an offset keeps the container time`() {
        assertContainerWins("VID_20200710_110201.mp4")
        assertContainerWins("VID_20200710_105759.mp4")
    }

    @Test
    fun `a name that is no offset of the instant keeps the container time`() {
        assertContainerWins("VID_20200710_112000.mp4")
        assertContainerWins("VID_20200710_100700.mp4")
    }

    @Test
    fun `a name equal to the container time keeps the container and its source`() =
        assertContainerWins("VID_20200710_100000.mp4")

    @Test
    fun `a name within two minutes of the container time keeps the container`() {
        assertContainerWins("VID_20200710_100130.mp4")
        assertContainerWins("VID_20200710_100200.mp4")
        assertContainerWins("VID_20200710_095800.mp4")
    }

    @Test
    fun `a date-only name never wins even when its midnight matches an offset`() =
        assertContainerWins("VID-20200710-WA0001.mp4")

    @Test
    fun `an epoch-millis name never wins`() = assertContainerWins("1594368000000.mp4")

    @Test
    fun `a name with an impossible date keeps the container time`() =
        assertContainerWins("VID_20201310_110000.mp4")

    @Test
    fun `a name without a date keeps the container time`() = assertContainerWins("holiday.mp4")

    // Standardize renames a video to PREFIX_yyyy-MM-dd_HH-mm-ss; that name still holds the shooting time (#84), so
    // a second run reaches the same answer instead of renaming the video back to the container's time.
    @Test
    fun `a standardized name five hours ahead of the container instant wins`() =
        assertNameWins("VID_2020-07-10_13-00-00.mp4", LocalDateTime.of(2020, 7, 10, 13, 0, 0))

    @Test
    fun `a standardized name with a collision suffix still wins`() =
        assertNameWins("VID_2020-07-10_13-00-00__dup2.mp4", LocalDateTime.of(2020, 7, 10, 13, 0, 0))

    @Test
    fun `a video carrying the IMG prefix counts too`() =
        assertNameWins("IMG_2020-07-10_13-00-00.mp4", LocalDateTime.of(2020, 7, 10, 13, 0, 0))

    @Test
    fun `a standardized name equal to the container time keeps the container`() =
        assertContainerWins("VID_2020-07-10_10-00-00.mp4")

    @Test
    fun `a standardized name that is no offset of the instant keeps the container`() =
        assertContainerWins("VID_2020-07-10_13-20-00.mp4")

    @Test
    fun `a standardized name with an impossible date keeps the container`() =
        assertContainerWins("VID_2020-13-10_13-00-00.mp4")

    @Test
    fun `other prefixes and separators are not the standardized form`() {
        assertContainerWins("Screenshot_2020-07-10_13-00-00.mp4")
        assertContainerWins("VID_2020-07-10 13-00-00.mp4")
        assertContainerWins("VID_2020-07-10_13:00:00.mp4")
    }

    // Real camera clocks drift from the container by seconds (17 s and 11 s on two phone videos of the fixtures).
    @Test
    fun `a camera clock a few seconds off the whole offset still wins`() {
        assertNameWins("VID_20200710_135943.mp4", LocalDateTime.of(2020, 7, 10, 13, 59, 43))
        assertNameWins("VID_20200710_135949.mp4", LocalDateTime.of(2020, 7, 10, 13, 59, 49))
    }

    @Test
    fun `a standardized name wins the same under another machine zone`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val name = "VID_2020-07-10_11-00-00.mp4"
        val entry = fs.addFile(fs.root, name)
        val tokyo = ZoneId.of("Asia/Tokyo")
        reader.setCaptureDateTime(entry, MediaDate(utcToLocal(UTC, tokyo), MediaDateSource.VIDEO_METADATA))

        val resolved = resolveMediaDate(entry, name, reader, tokyo)

        assertEquals(MediaDate(LocalDateTime.of(2020, 7, 10, 11, 0, 0), MediaDateSource.FILENAME), resolved)
    }

    // The hour clocks go back repeats a local time: the reading is the earlier instant, an hour off at worst, which the
    // 15-minute grid of offsets still accepts.
    @Test
    fun `a video shot in the hour clocks go back counts at either of its two instants`() {
        for (utc in listOf(LocalDateTime.of(2020, 10, 25, 0, 30, 0), LocalDateTime.of(2020, 10, 25, 1, 30, 0))) {
            val shot = utc.plusHours(5)
            val name = shot.format(DateTimeFormatter.ofPattern("'VID_'yyyyMMdd_HHmmss'.mp4'"))
            val fs = FakeFileSystemRepository()
            val entry = fs.addFile(fs.root, name)
            val reader = FakeMediaMetadataReader()
            reader.setCaptureDateTime(entry, MediaDate(utcToLocal(utc, ZONE), MediaDateSource.VIDEO_METADATA))

            assertEquals(MediaDate(shot, MediaDateSource.FILENAME), resolveMediaDate(entry, name, reader, ZONE))
        }
    }

    @Test
    fun `an image keeps its EXIF time whatever its name says`() {
        val expected = MediaDate(utcToLocal(UTC, ZONE), MediaDateSource.EXIF)
        assertEquals(expected, resolve("IMG_20200710_110000.jpg", MediaDateSource.EXIF))
    }

    @Test
    fun `the result does not depend on the machine zone`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val name = "VID_20200710_110000.mp4"
        val entry = fs.addFile(fs.root, name)
        val tokyo = ZoneId.of("Asia/Tokyo")
        reader.setCaptureDateTime(entry, MediaDate(utcToLocal(UTC, tokyo), MediaDateSource.VIDEO_METADATA))

        val resolved = resolveMediaDate(entry, name, reader, tokyo)

        assertEquals(MediaDate(LocalDateTime.of(2020, 7, 10, 11, 0, 0), MediaDateSource.FILENAME), resolved)
    }

    @Test
    fun `a video with no container date still falls back to its name`() {
        val fs = FakeFileSystemRepository()
        val name = "VID_20200710_110000.mp4"
        val entry = fs.addFile(fs.root, name)

        val resolved = resolveMediaDate(entry, name, FakeMediaMetadataReader(), ZONE)

        assertEquals(MediaDate(LocalDateTime.of(2020, 7, 10, 11, 0, 0), MediaDateSource.FILENAME), resolved)
    }
}
