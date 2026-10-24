package app.osprey.android

import app.osprey.settings.FolderSetting
import app.osprey.settings.InputFolderSetting
import app.osprey.settings.InputMode
import app.osprey.settings.MediaSettings
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val OUTPUT = "output"
private const val INPUTS = "inputs"
private const val REFERENCE = "ref"
private const val MODE = "mode"

/**
 * The saved shape of [MediaSettings], with no Android type in it so the rules can be tested on the JVM.
 * A folder is only trusted while [resolve] hands it back: the persisted grant can be revoked, and a revoked
 * one must never be silently reused, so a folder [resolve] cannot find is dropped on load. A library chosen
 * before Settings existed is read as the output, which keeps its folder for an existing user.
 *
 * @param resolve The folder a saved reference stands for, or null when it can no longer be used.
 */
internal class SettingsCodec(private val resolve: (String) -> FolderSetting?) {

    fun encode(settings: MediaSettings): String {
        val inputs = JSONArray()
        for (input in settings.inputs) {
            inputs.put(JSONObject().put(REFERENCE, input.folder.reference).put(MODE, input.mode.name))
        }
        return JSONObject().put(OUTPUT, settings.output?.reference.orEmpty()).put(INPUTS, inputs).toString()
    }

    /**
     * The settings saved as [text]. With no text, or text that cannot be read, nothing is saved yet: the
     * settings are empty, except that [legacyLibrary] (the library picked before Settings existed) is the output.
     */
    fun decode(text: String?, legacyLibrary: String?): MediaSettings {
        val json = text?.let(::parse) ?: return MediaSettings(output = folderOf(legacyLibrary))
        val inputs = json.optJSONArray(INPUTS)?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::inputOf) }
        }.orEmpty()
        return MediaSettings(output = folderOf(json.optString(OUTPUT)), inputs = inputs)
    }

    @Suppress("SwallowedException") // Unreadable saved settings mean "nothing saved yet", never a crash.
    private fun parse(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (e: JSONException) {
        null
    }

    private fun inputOf(entry: JSONObject): InputFolderSetting? {
        val folder = folderOf(entry.optString(REFERENCE)) ?: return null
        val mode = InputMode.entries.firstOrNull { it.name == entry.optString(MODE) } ?: InputMode.COPY
        return InputFolderSetting(folder, mode)
    }

    private fun folderOf(reference: String?): FolderSetting? =
        if (reference.isNullOrBlank()) null else resolve(reference)
}
