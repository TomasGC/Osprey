package app.osprey.android

import android.content.Context
import android.net.Uri
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore

private const val PREFS_NAME = "osprey_config"
private const val KEY_SETTINGS = "settings_json"
private const val KEY_LEGACY_LIBRARY_URI = "library_tree_uri"

/**
 * [SettingsStore] over the app's preferences. What is saved, and which folders are still trusted, is
 * [SettingsCodec]'s; this class only reads and writes the preference and asks the system whether a grant is held.
 */
class AndroidSettingsStore(private val context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val codec = SettingsCodec { reference ->
        val uri = Uri.parse(reference)
        if (hasGrant(context, uri)) folderSetting(context, uri) else null
    }

    override fun load(): MediaSettings =
        codec.decode(prefs.getString(KEY_SETTINGS, null), prefs.getString(KEY_LEGACY_LIBRARY_URI, null))

    override fun save(settings: MediaSettings) {
        prefs.edit().putString(KEY_SETTINGS, codec.encode(settings)).apply()
    }

    override fun hasSavedSettings(): Boolean = prefs.contains(KEY_SETTINGS)
}
