package app.osprey.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderLocationTest {

    private fun location(volume: String, path: String) = FolderLocation.of(volume, path)

    @Test
    fun `of splits on both separators and drops empty and dot segments`() {
        val location = FolderLocation.of("C:", "\\Users\\me//Pictures/./WhatsApp\\")

        assertEquals(listOf("users", "me", "pictures", "whatsapp"), location.segments)
    }

    @Test
    fun `a folder contains itself`() {
        assertTrue(location("primary", "Pictures").contains(location("primary", "Pictures")))
    }

    @Test
    fun `a folder contains its descendants but not the reverse`() {
        val parent = location("primary", "Pictures")
        val child = location("primary", "Pictures/WhatsApp/Media")

        assertTrue(parent.contains(child))
        assertFalse(child.contains(parent))
    }

    @Test
    fun `a sibling whose name only starts the same is not contained`() {
        assertFalse(location("primary", "Pictures").contains(location("primary", "Pictures2")))
    }

    @Test
    fun `folders on different volumes never contain each other`() {
        assertFalse(location("primary", "Pictures").contains(location("1234-ABCD", "Pictures")))
    }

    @Test
    fun `a volume root contains every folder of that volume`() {
        assertTrue(location("primary", "").contains(location("primary", "DCIM/Camera")))
    }

    @Test
    fun `comparison ignores case`() {
        assertTrue(location("C:", "Users/Me").contains(location("c:", "users/me/Pictures")))
    }

    @Test
    fun `overlaps is true in both directions and false for siblings`() {
        val parent = location("primary", "Pictures")
        val child = location("primary", "Pictures/Trips")
        val sibling = location("primary", "DCIM")

        assertTrue(parent.overlaps(child))
        assertTrue(child.overlaps(parent))
        assertFalse(parent.overlaps(sibling))
    }
}
