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
}
