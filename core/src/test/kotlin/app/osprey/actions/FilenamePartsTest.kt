package app.osprey.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenamePartsTest {

    @Test
    fun `the suffix is the last extension, dot included`() {
        assertEquals("photo.edit" to ".jpg", splitStemAndSuffix("photo.edit.jpg"))
    }

    @Test
    fun `a name with no dot has no suffix`() {
        assertEquals("README" to "", splitStemAndSuffix("README"))
    }

    @Test
    fun `a name whose only dot is its first character has no suffix`() {
        assertEquals(".bashrc" to "", splitStemAndSuffix(".bashrc"))
    }

    @Test
    fun `a trailing dot is the suffix`() {
        assertEquals("name" to ".", splitStemAndSuffix("name."))
    }
}
