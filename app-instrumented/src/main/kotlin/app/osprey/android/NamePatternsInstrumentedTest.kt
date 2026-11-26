package app.osprey.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.osprey.actions.visual.burstId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The name patterns on Android's own regex engine, which is not the JVM's that the unit tests run on (#141). */
@RunWith(AndroidJUnit4::class)
class NamePatternsInstrumentedTest {

    @Test
    fun burstIdsAreReadInAnyCaseButNotInsideAWord() {
        assertEquals("1", burstId("img_0000_burst1.jpg"))
        assertNull(burstId("sunburst2.jpg"))
        assertNull(burstId("éburst3.jpg"))
        assertNull(burstId("e\u0301burst3.jpg"))
        assertNull(burstId("star-burst1.jpg"))
        assertEquals("6", burstId("2018-BURST6.jpg"))
    }
}
