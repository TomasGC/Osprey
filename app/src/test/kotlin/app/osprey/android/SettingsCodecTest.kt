package app.osprey.android

import app.osprey.settings.FolderLocation
import app.osprey.settings.FolderSetting
import app.osprey.settings.InputFolderSetting
import app.osprey.settings.InputMode
import app.osprey.settings.MediaSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsCodecTest {

    private fun folder(path: String) = FolderSetting("tree:$path", FolderLocation.of("primary", path), path)

    private val pictures = folder("Pictures")
    private val camera = folder("DCIM/Camera")
    private val whatsapp = folder("WhatsApp")
    private val granted = mutableMapOf(
        pictures.reference to pictures,
        camera.reference to camera,
        whatsapp.reference to whatsapp,
    )
    private val asked = mutableListOf<String>()
    private val codec = SettingsCodec { reference ->
        asked += reference
        granted[reference]
    }

    private fun settings(output: FolderSetting?, vararg inputs: InputFolderSetting) =
        MediaSettings(output = output, inputs = inputs.toList())

    @Test
    fun `settings survive a save and a load`() {
        val saved = settings(pictures, InputFolderSetting(camera, InputMode.MOVE), InputFolderSetting(whatsapp))

        assertEquals(saved, codec.decode(codec.encode(saved), legacyLibrary = null))
    }

    @Test
    fun `empty settings survive a save and a load`() {
        assertEquals(MediaSettings(), codec.decode(codec.encode(MediaSettings()), legacyLibrary = null))
    }

    @Test
    fun `the saved text keeps the keys earlier versions wrote`() {
        val json = JSONObject(codec.encode(settings(pictures, InputFolderSetting(camera, InputMode.MOVE))))

        assertEquals("tree:Pictures", json.getString("output"))
        val input = json.getJSONArray("inputs").getJSONObject(0)
        assertEquals("tree:DCIM/Camera", input.getString("ref"))
        assertEquals("MOVE", input.getString("mode"))
    }

    @Test
    fun `an output whose grant is gone is dropped and the inputs stay`() {
        val text = codec.encode(settings(pictures, InputFolderSetting(camera)))
        granted.remove(pictures.reference)

        assertEquals(settings(null, InputFolderSetting(camera)), codec.decode(text, legacyLibrary = null))
    }

    @Test
    fun `an input whose grant is gone is dropped and the others keep their order`() {
        val text = codec.encode(
            settings(pictures, InputFolderSetting(camera), InputFolderSetting(whatsapp, InputMode.MOVE)),
        )
        granted.remove(camera.reference)

        assertEquals(
            settings(pictures, InputFolderSetting(whatsapp, InputMode.MOVE)),
            codec.decode(text, legacyLibrary = null),
        )
    }

    @Test
    fun `an unknown mode is read as Copy`() {
        val text = """{"output":"tree:Pictures","inputs":[{"ref":"tree:DCIM/Camera","mode":"SYNC"}]}"""

        assertEquals(settings(pictures, InputFolderSetting(camera, InputMode.COPY)), codec.decode(text, null))
    }

    @Test
    fun `an input without a mode is read as Copy`() {
        val text = """{"output":"tree:Pictures","inputs":[{"ref":"tree:DCIM/Camera"}]}"""

        assertEquals(InputMode.COPY, codec.decode(text, null).inputs.single().mode)
    }

    @Test
    fun `an input without a reference is skipped without asking about a grant`() {
        val text = """{"output":"","inputs":[{"mode":"MOVE"},{"ref":"","mode":"MOVE"},"not an object"]}"""

        assertEquals(MediaSettings(), codec.decode(text, null))
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a saved text with no output and no inputs is empty settings`() {
        assertEquals(MediaSettings(), codec.decode("{}", legacyLibrary = "tree:Pictures"))
    }

    @Test
    fun `readable saved settings win over a library from before Settings existed`() {
        val text = codec.encode(settings(camera))

        assertEquals(settings(camera), codec.decode(text, legacyLibrary = pictures.reference))
    }

    @Test
    fun `with nothing saved, the library from before Settings existed is the output`() {
        assertEquals(settings(pictures), codec.decode(text = null, legacyLibrary = pictures.reference))
    }

    @Test
    fun `with unreadable saved text, the old library is the output`() {
        assertEquals(settings(pictures), codec.decode("{not json", legacyLibrary = pictures.reference))
    }

    @Test
    fun `an old library whose grant is gone is not used`() {
        granted.remove(pictures.reference)

        assertNull(codec.decode(text = null, legacyLibrary = pictures.reference).output)
    }

    @Test
    fun `with nothing saved and no old library the settings are empty`() {
        assertEquals(MediaSettings(), codec.decode(text = null, legacyLibrary = null))
        assertEquals(MediaSettings(), codec.decode(text = null, legacyLibrary = " "))
        assertEquals(emptyList<String>(), asked)
    }
}
