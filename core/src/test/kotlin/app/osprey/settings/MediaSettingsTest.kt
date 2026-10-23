package app.osprey.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSettingsTest {

    private fun folder(path: String, volume: String = "primary") =
        FolderSetting("ref:$volume:$path", FolderLocation.of(volume, path), path.substringAfterLast('/'))

    private val pictures = folder("Pictures")
    private val whatsapp = folder("Android/media/com.whatsapp/WhatsApp/Media")
    private val camera = folder("DCIM/Camera")

    private fun accepted(result: SettingsResult): MediaSettings = (result as SettingsResult.Accepted).settings

    private fun rejected(result: SettingsResult): SettingsResult.Rejected = result as SettingsResult.Rejected

    @Test
    fun `an empty settings has no output and no inputs`() {
        val settings = MediaSettings()

        assertNull(settings.output)
        assertTrue(settings.inputs.isEmpty())
        assertTrue(!settings.isConfigured)
    }

    @Test
    fun `setting an output configures the settings`() {
        val settings = accepted(MediaSettings().withOutput(pictures))

        assertEquals(pictures, settings.output)
        assertTrue(settings.isConfigured)
    }

    @Test
    fun `an added input defaults to Copy`() {
        val settings = accepted(accepted(MediaSettings().withOutput(pictures)).addInput(camera))

        assertEquals(listOf(InputFolderSetting(camera, InputMode.COPY)), settings.inputs)
    }

    @Test
    fun `inputs keep the order they were added in`() {
        val base = accepted(MediaSettings().withOutput(pictures))
        val settings = accepted(accepted(base.addInput(camera)).addInput(whatsapp))

        assertEquals(listOf(camera, whatsapp), settings.inputs.map { it.folder })
    }

    @Test
    fun `an input inside the output is rejected`() {
        val base = accepted(MediaSettings().withOutput(pictures))

        val result = rejected(base.addInput(folder("Pictures/Trips")))

        assertEquals(SettingsRejection.INPUT_INSIDE_OUTPUT, result.reason)
        assertEquals(pictures, result.conflictsWith)
    }

    @Test
    fun `an input equal to the output is rejected`() {
        val base = accepted(MediaSettings().withOutput(pictures))

        assertEquals(SettingsRejection.INPUT_INSIDE_OUTPUT, rejected(base.addInput(pictures)).reason)
    }

    @Test
    fun `an input containing the output is rejected`() {
        val base = accepted(MediaSettings().withOutput(folder("Pictures/Library")))

        val result = rejected(base.addInput(pictures))

        assertEquals(SettingsRejection.INPUT_CONTAINS_OUTPUT, result.reason)
    }

    @Test
    fun `an input overlapping another input is rejected in both directions`() {
        val base = accepted(accepted(MediaSettings().withOutput(pictures)).addInput(folder("DCIM")))

        assertEquals(SettingsRejection.INPUT_OVERLAPS_INPUT, rejected(base.addInput(camera)).reason)
        val nested = accepted(accepted(MediaSettings().withOutput(pictures)).addInput(camera))
        assertEquals(SettingsRejection.INPUT_OVERLAPS_INPUT, rejected(nested.addInput(folder("DCIM"))).reason)
    }

    @Test
    fun `the same input twice is rejected`() {
        val base = accepted(accepted(MediaSettings().withOutput(pictures)).addInput(camera))

        val result = rejected(base.addInput(camera))

        assertEquals(SettingsRejection.INPUT_OVERLAPS_INPUT, result.reason)
        assertEquals(camera, result.conflictsWith)
    }

    @Test
    fun `an input can be added before any output is set`() {
        val settings = accepted(MediaSettings().addInput(camera))

        assertEquals(listOf(camera), settings.inputs.map { it.folder })
    }

    @Test
    fun `an output overlapping an existing input is rejected`() {
        val base = accepted(MediaSettings().addInput(camera))

        val result = rejected(base.withOutput(folder("DCIM")))

        assertEquals(SettingsRejection.OUTPUT_OVERLAPS_INPUT, result.reason)
        assertEquals(camera, result.conflictsWith)
    }

    @Test
    fun `changing the output to a free folder keeps the inputs`() {
        val base = accepted(accepted(MediaSettings().withOutput(pictures)).addInput(camera))

        val settings = accepted(base.withOutput(folder("Library")))

        assertEquals("Library", settings.output?.displayName)
        assertEquals(listOf(camera), settings.inputs.map { it.folder })
    }

    @Test
    fun `removing an input drops it and leaves the rest`() {
        val base = accepted(accepted(accepted(MediaSettings().withOutput(pictures)).addInput(camera)).addInput(whatsapp))

        val settings = base.removeInput(camera.reference)

        assertEquals(listOf(whatsapp), settings.inputs.map { it.folder })
    }

    @Test
    fun `removing an unknown input changes nothing`() {
        val base = accepted(MediaSettings().withOutput(pictures))

        assertEquals(base, base.removeInput("nope"))
    }

    @Test
    fun `withMode changes only that input`() {
        val base = accepted(accepted(accepted(MediaSettings().withOutput(pictures)).addInput(camera)).addInput(whatsapp))

        val settings = base.withMode(whatsapp.reference, InputMode.MOVE)

        assertEquals(listOf(InputMode.COPY, InputMode.MOVE), settings.inputs.map { it.mode })
    }

    @Test
    fun `a rejection explains itself and names the conflicting folder`() {
        val base = accepted(MediaSettings().withOutput(pictures))

        val message = rejected(base.addInput(folder("Pictures/Trips"))).message

        assertTrue(message.contains("Pictures"))
        assertTrue(message.isNotBlank())
    }
}
