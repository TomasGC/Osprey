package app.osprey.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.settings.FolderSetting
import app.osprey.settings.InputMode
import app.osprey.settings.MediaSettings
import app.osprey.settings.SettingsSession
import app.osprey.settings.SettingsStore

/**
 * The Settings screen's state over a [SettingsSession]: every accepted change is saved at once, a
 * refused one saves nothing and sets [message]. [onOutputChosen] opens the output as the library.
 * A folder grant that no setting refers to any more is handed to [releaseGrant], so refused picks
 * and removed folders never pile up against the system's limit on persisted grants.
 */
class SettingsState(
    private val store: SettingsStore,
    private val onOutputChosen: (FolderSetting) -> Unit,
    private val releaseGrant: (String) -> Unit,
) {

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
        settings.output?.let(onOutputChosen)
        showing = !settings.isConfigured || !store.hasSavedSettings()
    }

    /** Leaves the screen, saving what is there so a library from before Settings is not asked about again. */
    fun close() {
        store.save(settings)
        showing = false
    }

    /** Makes [folder] the output and opens it as the library, unless it overlaps an input. */
    fun chooseOutput(folder: FolderSetting) {
        val previous = settings.output?.reference
        if (update(session.withOutput(folder))) onOutputChosen(folder)
        releaseUnused(previous, folder.reference)
    }

    fun addInput(folder: FolderSetting) {
        update(session.addInput(folder))
        releaseUnused(folder.reference)
    }

    fun removeInput(reference: String) {
        update(session.removeInput(reference))
        releaseUnused(reference)
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

    private fun releaseUnused(vararg references: String?) {
        for (reference in references.filterNotNull().distinct()) {
            if (!isUsed(reference)) releaseGrant(reference)
        }
    }

    private fun isUsed(reference: String): Boolean =
        settings.output?.reference == reference || settings.inputs.any { it.folder.reference == reference }
}
