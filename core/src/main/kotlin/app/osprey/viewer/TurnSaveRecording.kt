package app.osprey.viewer

import app.osprey.history.FileRecord
import app.osprey.history.FileResult
import app.osprey.history.FileState
import app.osprey.history.RunRecorder
import app.osprey.jobs.JobOutcome

private const val DEGREES_IN_QUARTER = 90

/**
 * Ends the run of one Save turn (#79): writes the photo as one file record, then closes the run. A refusal and a
 * failure both end it as failed, with the reason the user was told, so a save that failed after the viewer was closed
 * on a photo nobody opens again is still found in the History.
 */
internal fun RunRecorder.Run.endSave(
    name: String,
    quarterTurns: Int,
    result: TurnSaveResult,
    before: FileState?,
    after: FileState?,
) {
    val failure = when (result) {
        is TurnSaveResult.Refused -> result.reason
        is TurnSaveResult.Failed -> result.reason
        else -> null
    }
    val note = (result as? TurnSaveResult.SavedWithNote)?.note
    val turned = "saved a ${quarterTurns * DEGREES_IN_QUARTER}\u00b0 clockwise turn" + note?.let { " ($it)" }.orEmpty()
    val outcome = if (failure == null) FileResult.Done else FileResult.Failed
    file(FileRecord(name, outcome, failure ?: turned, before, after))
    val summary = "${ViewTurns.SAVE_TURN}: $name" + result.userMessage?.let { ": $it" }.orEmpty()
    end(JobOutcome(summary, failures = if (failure == null) 0 else 1))
}
