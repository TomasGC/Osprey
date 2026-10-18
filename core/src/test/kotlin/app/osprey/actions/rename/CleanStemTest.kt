package app.osprey.actions.rename

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanStemTest {

    @Test
    fun `strips a paren suffix`() {
        assertEquals("photo", cleanStem("photo (2)"))
    }

    @Test
    fun `strips a single-digit underscore suffix`() {
        assertEquals("photo", cleanStem("photo_1"))
    }

    @Test
    fun `leaves a multi-digit underscore suffix alone`() {
        // Must never collide with real camera/sequence names (_NN+).
        assertEquals("photo_12", cleanStem("photo_12"))
        assertEquals("DSC_0067", cleanStem("DSC_0067"))
        assertEquals("IMG_20170425_152602", cleanStem("IMG_20170425_152602"))
        assertEquals("video_001", cleanStem("video_001"))
    }

    @Test
    fun `strips a dash-copy suffix`() {
        assertEquals("photo", cleanStem("photo - Copy"))
        assertEquals("photo", cleanStem("photo - copy"))
        assertEquals("photo", cleanStem("photo - Copy (2)"))
    }

    @Test
    fun `strips a word-copy suffix`() {
        assertEquals("photo", cleanStem("photo copy"))
        assertEquals("photo", cleanStem("photo copy 2"))
    }

    @Test
    fun `strips stacked suffixes over multiple passes`() {
        assertEquals("photo", cleanStem("photo (1) (2)"))
    }

    @Test
    fun `stops after maxPasses`() {
        // Three stackable suffixes, but maxPasses=1 only strips once.
        assertEquals("photo (1) (2)", cleanStem("photo (1) (2) (3)", maxPasses = 1))
    }

    @Test
    fun `leaves a name with no suffix unchanged`() {
        assertEquals("photo", cleanStem("photo"))
    }
}
