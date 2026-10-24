package app.osprey.media

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UtcConversionTest {

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `utcToLocal adds the summer offset`() {
        assertEquals(
            LocalDateTime.of(2018, 5, 5, 12, 0, 0),
            utcToLocal(LocalDateTime.of(2018, 5, 5, 10, 0, 0), paris),
        )
    }

    @Test
    fun `utcToLocal adds the winter offset`() {
        assertEquals(
            LocalDateTime.of(2018, 1, 5, 11, 0, 0),
            utcToLocal(LocalDateTime.of(2018, 1, 5, 10, 0, 0), paris),
        )
    }

    @Test
    fun `utcToLocal leaves a UTC reading unchanged in UTC`() {
        val utc = LocalDateTime.of(2020, 6, 1, 8, 30, 0)

        assertEquals(utc, utcToLocal(utc, ZoneId.of("UTC")))
    }

    @Test
    fun `utcToLocal moves to the next day east of UTC`() {
        assertEquals(
            LocalDateTime.of(2020, 1, 2, 8, 30, 0),
            utcToLocal(LocalDateTime.of(2020, 1, 1, 23, 30, 0), ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun `utcToLocal moves to the previous day west of UTC`() {
        assertEquals(
            LocalDateTime.of(2019, 12, 31, 19, 30, 0),
            utcToLocal(LocalDateTime.of(2020, 1, 1, 0, 30, 0), ZoneId.of("America/New_York")),
        )
    }

    @Test
    fun `utcToLocal uses the system zone when none is given`() {
        val utc = LocalDateTime.of(2020, 7, 1, 12, 0, 0)
        val offset = ZoneId.systemDefault().rules.getOffset(utc.toInstant(java.time.ZoneOffset.UTC))

        assertEquals(utc.plusSeconds(offset.totalSeconds.toLong()), utcToLocal(utc))
    }
}
