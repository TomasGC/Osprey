package app.osprey.viewer

/** What saving a turn into a photo's file came to (#61). */
sealed interface TurnSaveResult {
    /** The file holds the turn now, or already did. */
    data object Saved : TurnSaveResult

    /** The file holds the turn, and there is something the user should know about it. */
    data class SavedWithNote(val note: String) : TurnSaveResult

    /** The file was not touched, for a reason the user can be told. */
    data class Refused(val reason: String) : TurnSaveResult

    /** The file system failed; whatever was written has been put back. */
    data class Failed(val reason: String) : TurnSaveResult
}

/** What to tell the user after a save, or null when it worked. */
val TurnSaveResult.userMessage: String?
    get() = when (this) {
        TurnSaveResult.Saved -> null
        is TurnSaveResult.SavedWithNote -> note
        is TurnSaveResult.Refused -> reason
        is TurnSaveResult.Failed -> "Could not save the turn: $reason"
    }

/** Whether the turn is in the file, with a note or without one. */
val TurnSaveResult.isSaved: Boolean
    get() = this == TurnSaveResult.Saved || this is TurnSaveResult.SavedWithNote

/** Why a pending turn cannot be saved, or null when it can: a turned HEIC says so before the user presses. */
fun saveTurnHint(name: String, pendingTurns: Int): String? =
    if (pendingTurns != 0 && !TurnSaver.canSave(name)) TurnSaver.UNSUPPORTED_FORMAT else null
