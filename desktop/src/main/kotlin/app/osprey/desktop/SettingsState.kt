package app.osprey.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.settings.InputMode
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsSession
import app.osprey.settings.SettingsStore
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The Settings screen's state over a [SettingsSession]: every accepted change is saved at once, a
 * refused one saves nothing and sets [message]. [onOutputChosen] opens the output as the library.
 */
class SettingsState(private val store: SettingsStore, private val onOutputChosen: (Path) -> Unit) {

    private var session by mutableStateOf(SettingsSession())
    var showing by mutableStateOf(false)

    val settings: MediaSettings get() = session.settings
    val message: String? get() = session.message

    /**
     * Loads the saved settings and opens their output as the library. The screen shows when there is
     * no output yet, and once for a library chosen before Settings existed.
     */
    fun start() {
        session = SettingsSession(store.load())
        settings.output?.let { onOutputChosen(Paths.get(it.reference)) }
        showing = !settings.isConfigured || !store.hasSavedSettings()
    }

    /** Leaves the screen, saving what is there so a library from before Settings is not asked about again. */
    fun close() {
        store.save(settings)
        showing = false
    }

    /** Makes [path] the output folder and opens it as the library, unless it overlaps an input. */
    fun chooseOutput(path: Path) {
        if (update(session.withOutput(folderSetting(path)))) onOutputChosen(path)
    }

    fun addInput(path: Path) {
        update(session.addInput(folderSetting(path)))
    }

    fun removeInput(reference: String) {
        update(session.removeInput(reference))
    }

    fun setInputMode(reference: String, mode: InputMode) {
        update(session.withMode(reference, mode))
    }

    /** Keeps [next], saving its settings if they changed. Returns whether they did. */
    private fun update(next: SettingsSession): Boolean {
        val changed = next.changedSince(session)
        if (changed) store.save(next.settings)
        session = next
        return changed
    }
}
