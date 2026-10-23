package app.osprey.android

import android.content.Context
import android.net.Uri
import app.osprey.settings.FolderSetting
import app.osprey.settings.InputFolderSetting
import app.osprey.settings.InputMode
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val PREFS_NAME = "osprey_config"
private const val KEY_SETTINGS = "settings_json"
private const val KEY_LEGACY_LIBRARY_URI = "library_tree_uri"
private const val OUTPUT = "output"
private const val INPUTS = "inputs"
private const val REFERENCE = "ref"
private const val MODE = "mode"

/**
 * [SettingsStore] over the app's preferences. A folder is only trusted while its persisted grant is
 * still held — the grant can be revoked, and a revoked one must never be silently reused — so a
 * folder without it is dropped on load. A library chosen before Settings existed is read as the
 * output, which keeps its folder for an existing user.
 */
class AndroidSettingsStore(private val context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): MediaSettings {
        val json = savedJson() ?: return MediaSettings(output = folderOf(prefs.getString(KEY_LEGACY_LIBRARY_URI, null)))
        val inputs = json.optJSONArray(INPUTS)?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::inputOf) }
        }.orEmpty()
        return MediaSettings(output = folderOf(json.optString(OUTPUT)), inputs = inputs)
    }

    override fun save(settings: MediaSettings) {
        val inputs = JSONArray()
        for (input in settings.inputs) {
            inputs.put(JSONObject().put(REFERENCE, input.folder.reference).put(MODE, input.mode.name))
        }
        val json = JSONObject().put(OUTPUT, settings.output?.reference.orEmpty()).put(INPUTS, inputs)
        prefs.edit().putString(KEY_SETTINGS, json.toString()).apply()
    }

    override fun hasSavedSettings(): Boolean = prefs.contains(KEY_SETTINGS)

    @Suppress("SwallowedException") // Unreadable saved settings mean "nothing saved yet", never a crash.
    private fun savedJson(): JSONObject? {
        val text = prefs.getString(KEY_SETTINGS, null) ?: return null
        return try {
            JSONObject(text)
        } catch (e: JSONException) {
            null
        }
    }

    private fun inputOf(entry: JSONObject): InputFolderSetting? {
        val folder = folderOf(entry.optString(REFERENCE)) ?: return null
        val mode = InputMode.entries.firstOrNull { it.name == entry.optString(MODE) } ?: InputMode.COPY
        return InputFolderSetting(folder, mode)
    }

    private fun folderOf(reference: String?): FolderSetting? {
        if (reference.isNullOrBlank()) return null
        val uri = Uri.parse(reference)
        return if (hasGrant(context, uri)) folderSetting(context, uri) else null
    }
}
