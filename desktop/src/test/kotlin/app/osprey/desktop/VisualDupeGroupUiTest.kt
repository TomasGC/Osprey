package app.osprey.desktop

import app.osprey.actions.visual.VisualDupeMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun member(name: String, sizeBytes: Long) = VisualDupeMember(FakeHandle, name, sizeBytes)

class VisualDupeGroupUiTest {

    @Test
    fun `a non-burst group keeps only its heaviest -- first -- member`() {
        val group = listOf(member("photo.jpg", 500), member("photo (1).jpg", 500))

        val uiGroups = toUiGroups(listOf(group))

        val ui = uiGroups.single()
        assertFalse(ui.isBurst)
        assertEquals(listOf("photo.jpg", "photo (1).jpg"), ui.members.map { it.name })
        assertTrue(ui.keep["photo.jpg"] == true)
        assertTrue(ui.keep["photo (1).jpg"] == false)
    }

    @Test
    fun `a burst set is pre-checked to keep every frame, heaviest first`() {
        val group = listOf(
            member("IMG_0001_BURST001.jpg", 100),
            member("IMG_0001_BURST002.jpg", 300),
            member("IMG_0001_BURST003.jpg", 200),
        )

        val ui = toUiGroups(listOf(group)).single()

        assertTrue(ui.isBurst)
        assertEquals(
            listOf("IMG_0001_BURST002.jpg", "IMG_0001_BURST003.jpg", "IMG_0001_BURST001.jpg"),
            ui.members.map { it.name },
        )
        assertTrue(ui.members.all { ui.keep[it.name] == true })
    }

    @Test
    fun `a burst set appends any non-burst member after the burst frames, unchecked`() {
        val group = listOf(
            member("IMG_0001_BURST001.jpg", 100),
            member("IMG_0001_BURST002.jpg", 300),
            member("unrelated_forward.jpg", 250),
        )

        val ui = toUiGroups(listOf(group)).single()

        assertTrue(ui.isBurst)
        assertEquals(
            listOf("IMG_0001_BURST002.jpg", "IMG_0001_BURST001.jpg", "unrelated_forward.jpg"),
            ui.members.map { it.name },
        )
        assertFalse(ui.keep["unrelated_forward.jpg"] == true)
    }

    @Test
    fun `a single burst-tagged member alone is not treated as a burst set`() {
        val group = listOf(member("IMG_0001_BURST001.jpg", 100), member("other.jpg", 200))

        val ui = toUiGroups(listOf(group)).single()

        assertFalse(ui.isBurst)
        assertTrue(ui.keep["IMG_0001_BURST001.jpg"] == true)
        assertTrue(ui.keep["other.jpg"] == false)
    }

    @Test
    fun `each group gets a 1-based index in scan order`() {
        val groups = listOf(listOf(member("a.jpg", 1), member("a2.jpg", 1)), listOf(member("b.jpg", 1), member("b2.jpg", 1)))

        val ui = toUiGroups(groups)

        assertEquals(listOf(1, 2), ui.map { it.index })
    }
}
