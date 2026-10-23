package app.osprey.settings

enum class SettingsRejection {
    INPUT_INSIDE_OUTPUT,
    INPUT_CONTAINS_OUTPUT,
    INPUT_OVERLAPS_INPUT,
    OUTPUT_OVERLAPS_INPUT,
}

/** The outcome of changing [MediaSettings]: the new settings, or why the change was refused. */
sealed interface SettingsResult {
    data class Accepted(val settings: MediaSettings) : SettingsResult

    /**
     * @property candidate The folder the user tried to set or add.
     * @property conflictsWith The folder already in the settings that it collides with.
     */
    data class Rejected(
        val reason: SettingsRejection,
        val candidate: FolderSetting,
        val conflictsWith: FolderSetting,
    ) : SettingsResult {

        /** A sentence for the user, naming both folders. */
        val message: String
            get() {
                val chosen = "\"${candidate.displayName}\""
                val other = "\"${conflictsWith.displayName}\""
                return when (reason) {
                    SettingsRejection.INPUT_INSIDE_OUTPUT ->
                        "$chosen is the output folder or inside it ($other). Choose a folder outside the output."
                    SettingsRejection.INPUT_CONTAINS_OUTPUT ->
                        "$chosen contains the output folder $other. Choose a folder that does not contain it."
                    SettingsRejection.INPUT_OVERLAPS_INPUT ->
                        "$chosen overlaps the input folder $other that is already in the list."
                    SettingsRejection.OUTPUT_OVERLAPS_INPUT ->
                        "The output folder $chosen overlaps the input folder $other. " +
                            "Remove that input first or choose another output."
                }
            }
    }
}
