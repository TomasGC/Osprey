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
}
