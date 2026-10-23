package app.osprey.desktop

import java.nio.file.Path
import java.nio.file.Paths
import org.json.JSONException
import org.json.JSONObject

private const val CONFIG_PATH_OVERRIDE_PROPERTY = "osprey.config.path"

internal const val LIBRARY_KEY = "library_path"
internal const val OUTPUT_KEY = "output_path"

/**
 * Library-path persistence, same contract as the POC's `config.py`: a plain JSON file under the
 * user's home directory, CLI-arg-wins-and-is-saved, else the last-used path. Setting one key
 * never clobbers any others already in the file (the POC's `save_config` loads-merges-saves, not
 * replaces). The path is read lazily on every call via [CONFIG_PATH_OVERRIDE_PROPERTY], not
 * cached, so tests can point it elsewhere without ever touching the real user's config file.
 * The settings (output and input folders) live in the same file: see [DesktopSettingsStore].
 */
object OspreyConfig {

    private fun configPath(): Path =
        System.getProperty(CONFIG_PATH_OVERRIDE_PROPERTY)?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("user.home"), ".osprey", "config.json")

    @Suppress("SwallowedException") // A missing/corrupt config file means "nothing saved yet", never a crash.
    internal fun loadConfig(): JSONObject {
        val file = configPath().toFile()
        if (!file.isFile) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (e: JSONException) {
            JSONObject()
        }
    }

    internal fun saveConfig(json: JSONObject) {
        val path = configPath()
        path.parent.toFile().mkdirs()
        path.toFile().writeText(json.toString(2))
    }

    fun loadLibraryPath(): Path? {
        val json = loadConfig()
        return if (json.has(LIBRARY_KEY)) Paths.get(json.getString(LIBRARY_KEY)) else null
    }

    /** Saves [path] as the library, which is also the settings' output folder; the inputs are kept. */
    fun saveLibraryPath(path: Path) {
        val json = loadConfig()
        val saved = path.toAbsolutePath().normalize().toString()
        json.put(LIBRARY_KEY, saved)
        json.put(OUTPUT_KEY, saved)
        saveConfig(json)
    }

    /** CLI arg wins and is saved for next time; otherwise the saved config. Null if neither is set. */
    fun resolveLibraryPath(cliArg: String?): Path? {
        if (!cliArg.isNullOrBlank()) {
            val path = Paths.get(cliArg).toAbsolutePath().normalize()
            saveLibraryPath(path)
            return path
        }
        return loadLibraryPath()
    }
}
