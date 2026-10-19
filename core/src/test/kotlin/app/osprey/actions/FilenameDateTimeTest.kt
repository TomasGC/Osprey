package app.osprey.actions

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameDateTimeTest {

    @Test
    fun `matches every known camera-sync naming convention`() {
        assertEquals(LocalDateTime.of(2021, 3, 5, 0, 0, 0), filenameDateTime("IMG-20210305-WA0007"))
        assertEquals(LocalDateTime.of(2021, 3, 5, 0, 0, 0), filenameDateTime("VID-20210305-WA0003"))
        assertEquals(LocalDateTime.of(2017, 4, 25, 15, 26, 2), filenameDateTime("IMG_20170425_152602"))
        assertEquals(LocalDateTime.of(2017, 4, 25, 15, 26, 2), filenameDateTime("VID_20170425_152602"))
        assertEquals(
            LocalDateTime.of(2018, 1, 1, 9, 30, 0),
            filenameDateTime("Ultimate_HDR_Camera_Original_20180101_093000"),
        )
        assertEquals(LocalDateTime.of(2018, 1, 1, 9, 30, 0), filenameDateTime("AR_EFFECT_20180101093000"))
        assertEquals(LocalDateTime.of(2016, 8, 28, 19, 30, 34), filenameDateTime("download_20160828_193034"))
        assertEquals(LocalDateTime.of(2016, 8, 28, 19, 30, 34), filenameDateTime("20160828_193034"))
        assertEquals(LocalDateTime.of(2019, 1, 1, 12, 0, 0), filenameDateTime("Screenshot_20190101-120000"))
        assertEquals(LocalDateTime.of(2019, 1, 1, 12, 0, 0), filenameDateTime("SmartSelect_20190101-120000"))
        assertEquals(LocalDateTime.of(2019, 1, 1, 12, 0, 0), filenameDateTime("CameraZOOM-20190101120000"))
    }

    @Test
    fun `parses a bare 13-digit epoch-millis timestamp`() {
        val dateTime = LocalDateTime.of(2021, 6, 15, 10, 0, 0)
        val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val stem = millis.toString()

        val expected = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        assertEquals(expected, filenameDateTime(stem))
    }

    @Test
    fun `returns null when nothing matches`() {
        assertNull(filenameDateTime("DSC_0067"))
        assertNull(filenameDateTime("random_file_name"))
    }

    @Test
    fun `returns null when the matched pattern encodes an impossible date`() {
        // Matches the IMG_YYYYMMDD_HHMMSS shape, but month 00 is impossible.
        assertNull(filenameDateTime("IMG_20170001_152602"))
    }

    @Test
    fun `leaves burst and sequence names unaffected`() {
        // Real camera/burst/chunk names must never be parsed as dates.
        assertNull(filenameDateTime("DSCPDC_0000_BURST20170101120000"))
    }
}
