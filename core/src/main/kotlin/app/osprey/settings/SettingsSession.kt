package app.osprey.settings

/**
 * The [MediaSettings] a Settings screen is editing, plus the [message] explaining the last refused
 * change. Immutable: every change returns the next session, so the screen only has to hold one value
 * and save [settings] whenever [changedSince] says they differ. A refused change keeps the settings and
 * sets the message; the next accepted change clears it.
 */
class SettingsSession(val settings: MediaSettings = MediaSettings(), val message: String? = null) {

    /** Sets the output folder, unless it overlaps an input. */
    fun withOutput(folder: FolderSetting): SettingsSession = after(settings.withOutput(folder))

    /** Adds [folder] as a Copy input, unless it overlaps the output or another input. */
    fun addInput(folder: FolderSetting): SettingsSession = after(settings.addInput(folder))

    fun removeInput(reference: String): SettingsSession = SettingsSession(settings.removeInput(reference))

    fun withMode(reference: String, mode: InputMode): SettingsSession =
        SettingsSession(settings.withMode(reference, mode))

    /** True if this session's settings differ from [other]'s: the moment to save. */
    fun changedSince(other: SettingsSession): Boolean = settings != other.settings

    private fun after(result: SettingsResult): SettingsSession = when (result) {
        is SettingsResult.Accepted -> SettingsSession(result.settings)
        is SettingsResult.Rejected -> SettingsSession(settings, result.message)
    }
}
