package app.osprey.settings

/**
 * Where [MediaSettings] are kept between launches: a JSON file on desktop, the app's preferences on
 * Android. [load] never fails — a missing or unreadable store means nothing is saved yet.
 */
interface SettingsStore {
    fun load(): MediaSettings

    fun save(settings: MediaSettings)

    /**
     * True once settings were saved through [save]. A library chosen before Settings existed is still
     * returned by [load] as the output, but is not "saved", so the app can show Settings once.
     */
    fun hasSavedSettings(): Boolean
}
