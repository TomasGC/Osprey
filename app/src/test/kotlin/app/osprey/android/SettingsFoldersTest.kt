package app.osprey.android

import app.osprey.settings.FolderLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFoldersTest {

    @Test
    fun `a primary storage document id splits into volume and path`() {
        assertEquals(
            FolderLocation.of("primary", "Pictures/WhatsApp"),
            folderLocationOf("primary:Pictures/WhatsApp"),
        )
    }

    @Test
    fun `an SD card document id keeps its volume apart from primary`() {
        val sdCard = folderLocationOf("1234-ABCD:DCIM")

        assertEquals("1234-abcd", sdCard.volume)
        assertFalse(folderLocationOf("primary:DCIM").overlaps(sdCard))
    }

    @Test
    fun `a volume root has no segments and contains everything on that volume`() {
        val root = folderLocationOf("primary:")

        assertTrue(root.segments.isEmpty())
        assertTrue(root.contains(folderLocationOf("primary:DCIM/Camera")))
    }

    @Test
    fun `an id without a colon is taken as a volume with no path`() {
        assertEquals(FolderLocation.of("raw", ""), folderLocationOf("raw"))
    }

    @Test
    fun `nested and sibling folders compare as expected`() {
        val pictures = folderLocationOf("primary:Pictures")

        assertTrue(pictures.contains(folderLocationOf("primary:Pictures/Trips")))
        assertFalse(pictures.contains(folderLocationOf("primary:Pictures2")))
    }
}
