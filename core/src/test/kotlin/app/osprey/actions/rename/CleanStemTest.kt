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

    @Test
    fun `tries the patterns in priority order, so a copy word wins over a trailing paren`() {
        assertEquals("photo", cleanStem("photo - Copy (2)", maxPasses = 1))
    }

    @Test
    fun `a stem that is only a suffix is left alone`() {
        assertEquals("(1)", cleanStem("(1)"))
        assertEquals("_1", cleanStem("_1"))
    }

    @Test
    fun `zero passes change nothing`() {
        assertEquals("photo (1)", cleanStem("photo (1)", maxPasses = 0))
    }

    @Test
    fun `strips a copy suffix written in any case`() {
        for (stem in listOf("photo - COPY", "photo - COPY (2)", "photo COPY", "photo COPY 2", "photo - cOpY")) {
            assertEquals(stem, "photo", cleanStem(stem))
        }
    }

    @Test
    fun `strips a French copy suffix`() {
        for (stem in listOf("photo - Copie", "photo - copie", "photo - COPIE", "photo - Copie (2)")) {
            assertEquals(stem, "photo", cleanStem(stem))
        }
        assertEquals("photo", cleanStem("photo (1) - Copie (2)"))
    }

    @Test
    fun `leaves names that only look like a French copy suffix`() {
        val names = listOf("photo-copie", "photo -copie", "photo- copie", "Ma copie", "photo - Copies", "- Copie")
        for (stem in names) {
            assertEquals(stem, cleanStem(stem))
        }
    }

    @Test
    fun `still strips an English copy suffix joined by a bare dash`() {
        assertEquals("photo", cleanStem("photo-copy"))
    }

    @Test
    fun `strips the suffixes of every language listed`() {
        for (language in copySuffixLanguages) {
            val word = language.word
            val bare = if (language.bareWord) listOf("photo $word", "photo $word 2") else emptyList()
            for (stem in listOf("photo - $word", "photo - $word (2)") + bare) {
                assertEquals(stem, "photo", cleanStem(stem))
            }
        }
    }
}
