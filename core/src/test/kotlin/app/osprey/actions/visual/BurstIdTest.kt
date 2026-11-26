package app.osprey.actions.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BurstIdTest {

    @Test
    fun `extracts the burst id`() {
        assertEquals("20170101120000", burstId("DSCPDC_0000_BURST20170101120000.JPG"))
    }

    @Test
    fun `the cover frame shares the same burst id`() {
        assertEquals("20170101120000", burstId("DSCPDC_0003_BURST20170101120000_COVER.JPG"))
    }

    @Test
    fun `returns null for a non-burst filename`() {
        assertNull(burstId("DSC_0815.JPG"))
        assertNull(burstId("IMG_20170425_152602.jpg"))
    }

    @Test
    fun `reads the digits after BURST wherever they sit in the name`() {
        assertEquals("20170101", burstId("a_BURST20170101_b.jpg"))
    }

    @Test
    fun `BURST with no digits after it is not a burst id`() {
        assertNull(burstId("BURST_cover.jpg"))
    }

    @Test
    fun `reads a burst id whatever the case of the name`() {
        assertEquals("20170101120000", burstId("dscpdc_0000_burst20170101120000.jpg"))
        assertEquals("001", burstId("Img_Burst001.jpg"))
        assertEquals("7", burstId("burst7.jpg"))
        assertEquals("5", burstId("0000BURST5.jpg"))
    }

    @Test
    fun `a word ending in burst is not a burst`() {
        assertNull(burstId("sunburst2.jpg"))
        assertNull(burstId("SUNBURST2.JPG"))
        assertNull(burstId("éburst3.jpg"))
    }

    @Test
    fun `a word is still a word across a hyphen or with its accent stored apart`() {
        assertNull(burstId("star-burst1.jpg"))
        assertNull(burstId("e\u0301burst3.jpg"))
        assertEquals("6", burstId("2018-BURST6.jpg"))
    }

    @Test
    fun `reads a burst id after a word ending in burst`() {
        assertEquals("4", burstId("sunburst2_burst4.jpg"))
    }
}
