package app.osprey.actions.generated

import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.utcToLocal
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileSpecTest {

    private val time = LocalDateTime.of(2020, 7, 10, 8, 15, 0)

    @Test
    fun `no metadata reads as nothing`() {
        assertNull(SpecMetadata.None.asMediaDate())
    }

    @Test
    fun `an EXIF time reads as itself`() {
        assertEquals(MediaDate(time, MediaDateSource.EXIF), SpecMetadata.Exif(time).asMediaDate())
    }

    @Test
    fun `a container reads as its UTC instant shown on the machine`() {
        val date = SpecMetadata.Container(time, 3 * 3600).asMediaDate()

        assertEquals(MediaDate(utcToLocal(time), MediaDateSource.VIDEO_METADATA), date)
    }

    @Test
    fun `an unset container reads as the 1904 epoch`() {
        val expected = MediaDate(LocalDateTime.of(1904, 1, 1, 0, 0), MediaDateSource.VIDEO_METADATA)

        assertEquals(expected, SpecMetadata.UnsetContainer.asMediaDate())
    }

    @Test
    fun `each id has its own content`() {
        val a = FileSpec(1, "a.jpg", SpecMetadata.None)
        val b = FileSpec(2, "a.jpg", SpecMetadata.None)

        assertNotEquals(String(a.content), String(b.content))
        assertEquals(String(a.content), String(a.copy(name = "other.jpg").content))
    }

    private val machine get() = machineOffsetSeconds(time)

    private fun spec(metadata: SpecMetadata, nameTime: LocalDateTime? = null, wallClock: Boolean = false) =
        FileSpec(1, "a.mp4", metadata, nameTime, wallClock)

    @Test
    fun `EXIF is the expected date whatever the name says`() {
        val named = time.plusHours(5)

        assertEquals(ExpectedDate.At(time), spec(SpecMetadata.Exif(time), named, wallClock = true).expectedDate())
    }

    @Test
    fun `a container at the machine's own offset is the expected date`() {
        val container = SpecMetadata.Container(time, machine)

        val expected = ExpectedDate.At(utcToLocal(time))

        assertEquals(expected, spec(container, utcToLocal(time), wallClock = true).expectedDate())
    }

    @Test
    fun `a container at another offset is the expected date when the name holds no clock time`() {
        val container = SpecMetadata.Container(time, machine + 3600)

        assertEquals(ExpectedDate.At(utcToLocal(time)), spec(container, nameTime = null).expectedDate())
    }

    @Test
    fun `a container at another offset under a name with a clock time is unclear`() {
        val container = SpecMetadata.Container(time, machine + 3600)

        assertEquals(ExpectedDate.Unclear, spec(container, time.plusHours(1), wallClock = true).expectedDate())
    }

    @Test
    fun `no metadata, or an unset container, is dated by the name, or undated without a date in it`() {
        assertEquals(ExpectedDate.At(time), spec(SpecMetadata.None, time, wallClock = true).expectedDate())
        assertEquals(ExpectedDate.At(time), spec(SpecMetadata.UnsetContainer, time).expectedDate())
        assertEquals(ExpectedDate.Undated, spec(SpecMetadata.None).expectedDate())
        assertEquals(ExpectedDate.Undated, spec(SpecMetadata.UnsetContainer).expectedDate())
    }
}
