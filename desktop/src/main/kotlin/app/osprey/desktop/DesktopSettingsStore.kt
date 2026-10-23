package app.osprey.desktop

import app.osprey.settings.FolderLocation
import app.osprey.settings.FolderSetting
import app.osprey.settings.InputFolderSetting
import app.osprey.settings.InputMode
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsStore
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import org.json.JSONArray
import org.json.JSONObject

private const val INPUTS_KEY = "input_folders"
private const val PATH_KEY = "path"
private const val MODE_KEY = "mode"

/**
 * [SettingsStore] over the same JSON file as [OspreyConfig]. A library saved before settings existed
 * is read as the output, so an existing user keeps the folder they already chose; an unreadable input
 * entry is skipped; every other key in the file is kept on save.
 */
class DesktopSettingsStore : SettingsStore {

    override fun load(): MediaSettings {
        val json = OspreyConfig.loadConfig()
        val outputPath = json.optString(OUTPUT_KEY).ifBlank { json.optString(LIBRARY_KEY) }
        val inputs = json.optJSONArray(INPUTS_KEY)?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::inputOf) }
        }.orEmpty()
        return MediaSettings(output = folderOf(outputPath), inputs = inputs)
    }

    /** Without an output, the saved library is forgotten too. */
    override fun save(settings: MediaSettings) {
        val json = OspreyConfig.loadConfig()
        val output = settings.output
        if (output != null) {
            json.put(OUTPUT_KEY, output.reference)
            json.put(LIBRARY_KEY, output.reference)
        } else {
            json.remove(OUTPUT_KEY)
            json.remove(LIBRARY_KEY)
        }
        val inputs = JSONArray()
        for (input in settings.inputs) {
            inputs.put(JSONObject().put(PATH_KEY, input.folder.reference).put(MODE_KEY, input.mode.name))
        }
        json.put(INPUTS_KEY, inputs)
        OspreyConfig.saveConfig(json)
    }

    /** True once [save] ran: the input list is written even when it is empty. */
    override fun hasSavedSettings(): Boolean = OspreyConfig.loadConfig().has(INPUTS_KEY)

    private fun inputOf(entry: JSONObject): InputFolderSetting? {
        val folder = folderOf(entry.optString(PATH_KEY)) ?: return null
        val mode = InputMode.entries.firstOrNull { it.name == entry.optString(MODE_KEY) } ?: InputMode.COPY
        return InputFolderSetting(folder, mode)
    }

    @Suppress("SwallowedException") // A path that no longer parses is skipped like any unreadable entry.
    private fun folderOf(path: String): FolderSetting? {
        if (path.isBlank()) return null
        return try {
            folderSetting(Paths.get(path))
        } catch (e: InvalidPathException) {
            null
        }
    }
}

/**
 * The [FolderSetting] of [path]: its absolute normalized path as the reference, the volume root as
 * the volume and the segments below it as the comparable location.
 */
fun folderSetting(path: Path): FolderSetting {
    val absolute = path.toAbsolutePath().normalize()
    val root = absolute.root
    val relative = if (root != null) root.relativize(absolute).toString() else absolute.toString()
    return FolderSetting(
        reference = absolute.toString(),
        location = FolderLocation.of(root?.toString().orEmpty(), relative),
        displayName = absolute.fileName?.toString() ?: absolute.toString(),
    )
}
