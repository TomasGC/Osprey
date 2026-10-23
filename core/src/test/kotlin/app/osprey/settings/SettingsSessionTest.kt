package app.osprey.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSessionTest {

    private fun folder(path: String) = FolderSetting("ref:$path", FolderLocation.of("primary", path), path.substringAfterLast('/'))

    private val library = folder("Pictures")
    private val camera = folder("DCIM/Camera")

    private fun configured() = SettingsSession().withOutput(library)

    @Test
    fun `a new session has empty settings and no message`() {
        val session = SettingsSession()

        assertEquals(MediaSettings(), session.settings)
        assertNull(session.message)
    }

    @Test
    fun `an accepted change updates the settings`() {
        val session = configured().addInput(camera)

        assertEquals(listOf(camera), session.settings.inputs.map { it.folder })
        assertNull(session.message)
    }

    @Test
    fun `a refused change keeps the settings and sets the message`() {
        val before = configured()

        val after = before.addInput(folder("Pictures/Trips"))

        assertEquals(before.settings, after.settings)
        assertTrue(after.message.orEmpty().contains("Pictures"))
    }

    @Test
    fun `the next accepted change clears the message`() {
        val refused = configured().addInput(folder("Pictures/Trips"))

        val session = refused.addInput(camera)

        assertNull(session.message)
    }

    @Test
    fun `removing an input and switching a mode keep the rest and clear the message`() {
        val refused = configured().addInput(camera).addInput(folder("Pictures/Trips"))

        val session = refused.withMode(camera.reference, InputMode.MOVE)

        assertEquals(listOf(InputMode.MOVE), session.settings.inputs.map { it.mode })
        assertNull(session.message)
        assertTrue(session.removeInput(camera.reference).settings.inputs.isEmpty())
    }

    @Test
    fun `changed tells whether the settings differ from another session`() {
        val before = configured()

        assertTrue(!before.addInput(folder("Pictures/Trips")).changedSince(before))
        assertTrue(before.addInput(camera).changedSince(before))
    }
}
