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

    @Test
    fun `parses a WhatsApp name as midnight of its date`() {
        assertEquals(LocalDateTime.of(2019, 3, 7, 0, 0, 0), filenameDateTime("IMG-20190307-WA0012"))
        assertEquals(LocalDateTime.of(2019, 3, 7, 0, 0, 0), filenameDateTime("VID-20190307-WA0012"))
    }

    @Test
    fun `returns null when a WhatsApp name encodes an impossible date`() {
        assertNull(filenameDateTime("IMG-20191301-WA0012"))
    }

    @Test
    fun `parses the largest 13-digit epoch-millis timestamp`() {
        val expected = LocalDateTime.ofInstant(Instant.ofEpochMilli(9_999_999_999_999L), ZoneId.systemDefault())

        assertEquals(expected, filenameDateTime("9999999999999"))
    }

    @Test
    fun `does not read a 12-digit or 14-digit number as epoch millis`() {
        assertNull(filenameDateTime("123456789012"))
        assertNull(filenameDateTime("12345678901234"))
    }

    @Test
    fun `filenameWallClock reads the camera patterns with a time of day`() {
        assertEquals(LocalDateTime.of(2020, 7, 10, 11, 0, 0), filenameWallClock("VID_20200710_110000"))
        assertEquals(LocalDateTime.of(2020, 7, 10, 11, 0, 0), filenameWallClock("Screenshot_20200710-110000"))
    }

    @Test
    fun `filenameWallClock ignores date-only and epoch names`() {
        assertNull(filenameWallClock("VID-20200710-WA0001"))
        assertNull(filenameWallClock("1594368000000"))
    }

    @Test
    fun `filenameWallClock returns null for an impossible date and for no match`() {
        assertNull(filenameWallClock("VID_20201310_110000"))
        assertNull(filenameWallClock("holiday"))
    }

    @Test
    fun `filenameWallClock reads a standardized name`() {
        assertEquals(LocalDateTime.of(2020, 7, 10, 13, 0, 0), filenameWallClock("VID_2020-07-10_13-00-00"))
        assertEquals(LocalDateTime.of(2020, 7, 10, 13, 0, 0), filenameWallClock("IMG_2020-07-10_13-00-00__dup2"))
    }

    @Test
    fun `filenameWallClock rejects other prefixes, separators and impossible dates in the standardized form`() {
        assertNull(filenameWallClock("Screenshot_2020-07-10_13-00-00"))
        assertNull(filenameWallClock("VID_2020-07-10 13-00-00"))
        assertNull(filenameWallClock("VID_2020-07-10_13:00:00"))
        assertNull(filenameWallClock("VID_2020-13-10_13-00-00"))
    }

    @Test
    fun `filenameWallClock reads every field of a standardized name from its own place`() {
        assertEquals(LocalDateTime.of(2021, 8, 17, 13, 45, 21), filenameWallClock("VID_2021-08-17_13-45-21"))
    }

    @Test
    fun `filenameWallClock needs a standardized name to start with its prefix`() {
        assertNull(filenameWallClock("holiday_VID_2020-07-10_13-00-00"))
    }

    @Test
    fun `filenameWallClock needs the exact separators of a standardized name`() {
        assertNull(filenameWallClock("VID-2020-07-10_13-00-00"))
        assertNull(filenameWallClock("VID_2020.07-10_13-00-00"))
        assertNull(filenameWallClock("VID_2020-07.10_13-00-00"))
        assertNull(filenameWallClock("VID_2020-07-10_13.00-00"))
        assertNull(filenameWallClock("VID_2020-07-10_13-00.00"))
    }

    @Test
    fun `filenameWallClock needs every field of a standardized name at its full width`() {
        assertNull(filenameWallClock("VID_20-07-10_13-00-00"))
        assertNull(filenameWallClock("VID_2020-7-10_13-00-00"))
        assertNull(filenameWallClock("VID_2020-07-1_13-00-00"))
        assertNull(filenameWallClock("VID_2020-07-10_1-00-00"))
        assertNull(filenameWallClock("VID_2020-07-10_13-0-00"))
        assertNull(filenameWallClock("VID_2020-07-10_13-00-0"))
    }

    // Standardize reads back what it wrote (#84): a renamed photo or video with no metadata keeps its date.
    @Test
    fun `filenameDateTime reads a standardized name`() {
        assertEquals(LocalDateTime.of(2016, 8, 28, 19, 30, 34), filenameDateTime("IMG_2016-08-28_19-30-34"))
        assertEquals(LocalDateTime.of(2016, 8, 28, 19, 30, 34), filenameDateTime("VID_2016-08-28_19-30-34__dup2"))
    }

    @Test
    fun `filenameDateTime rejects other prefixes, separators and impossible dates in the standardized form`() {
        assertNull(filenameDateTime("Screenshot_2016-08-28_19-30-34"))
        assertNull(filenameDateTime("VID_2016-08-28 19-30-34"))
        assertNull(filenameDateTime("VID_2016-08-28_19:30:34"))
        assertNull(filenameDateTime("VID_2016-13-28_19-30-34"))
    }
}
