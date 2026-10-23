package app.osprey.settings

/**
 * The folders Osprey works with: one [output] (the library root holding the year folders and the
 * Trash) and any number of [inputs] to gather media from. Immutable; every change that could break
 * the folder rules returns a [SettingsResult], so an overlapping layout can never be saved.
 */
data class MediaSettings(
    val output: FolderSetting? = null,
    val inputs: List<InputFolderSetting> = emptyList(),
) {
    /** True once an output folder is chosen: the app can open a library. */
    val isConfigured: Boolean get() = output != null

    /** Sets the output, unless it overlaps an input. */
    fun withOutput(folder: FolderSetting): SettingsResult {
        val clash = inputs.firstOrNull { it.folder.location.overlaps(folder.location) }
        if (clash != null) {
            return SettingsResult.Rejected(SettingsRejection.OUTPUT_OVERLAPS_INPUT, folder, clash.folder)
        }
        return SettingsResult.Accepted(copy(output = folder))
    }

    /** Appends [folder] as a Copy input, unless it is, contains or sits inside the output, or overlaps an input. */
    fun addInput(folder: FolderSetting): SettingsResult {
        rejection(folder)?.let { return it }
        return SettingsResult.Accepted(copy(inputs = inputs + InputFolderSetting(folder)))
    }

    fun removeInput(reference: String): MediaSettings =
        copy(inputs = inputs.filterNot { it.folder.reference == reference })

    fun withMode(reference: String, mode: InputMode): MediaSettings = copy(
        inputs = inputs.map { if (it.folder.reference == reference) it.copy(mode = mode) else it },
    )

    private fun rejection(folder: FolderSetting): SettingsResult.Rejected? {
        val out = output
        val reason = when {
            out != null && out.location.contains(folder.location) -> SettingsRejection.INPUT_INSIDE_OUTPUT to out
            out != null && folder.location.contains(out.location) -> SettingsRejection.INPUT_CONTAINS_OUTPUT to out
            else -> null
        }
        if (reason != null) return SettingsResult.Rejected(reason.first, folder, reason.second)
        val clash = inputs.firstOrNull { it.folder.location.overlaps(folder.location) } ?: return null
        return SettingsResult.Rejected(SettingsRejection.INPUT_OVERLAPS_INPUT, folder, clash.folder)
    }
}
