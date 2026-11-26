package app.osprey.android

import app.osprey.actions.visual.VisualDupeMember
import app.osprey.filesystem.FakeFileSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDupeGroupUiTest {

    private val fs = FakeFileSystemRepository()

    private fun member(name: String, sizeBytes: Long) =
        VisualDupeMember(fs.addFile(fs.root, name), name, sizeBytes)

    @Test
    fun `a lowercase burst set keeps every frame, heaviest first, and not the other member`() {
        val group = listOf(
            member("other.jpg", 400),
            member("img_0001_burst1.jpg", 100),
            member("img_0002_burst1.jpg", 300),
        )

        val ui = toUiGroups(listOf(group)).single()

        assertTrue(ui.isBurst)
        assertEquals(
            listOf("img_0002_burst1.jpg", "img_0001_burst1.jpg", "other.jpg"),
            ui.members.map { it.name },
        )
        assertEquals(listOf(true, true, false), ui.members.map { ui.keep[it.name] })
    }
}
