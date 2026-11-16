package app.osprey.actions.trash

import app.osprey.jobs.JobOutcome
import app.osprey.jobs.withStoppedNote

/** What a Trash delete says about itself once over (#41, #83). */
fun clearOutcome(result: TrashClearResult): JobOutcome = JobOutcome(
    summary = withStoppedNote("Trash: ${result.deleted} deleted, ${result.failures.size} failed", result.cancelled),
    lines = clearResultLines(result),
    failures = result.failures.size,
    stopped = result.cancelled,
)

/** What a Trash restore says about itself once over; [conflicts] are the names still waiting for a decision. */
fun restoreOutcome(result: TrashRestoreResult, conflicts: Int): JobOutcome = JobOutcome(
    summary = withStoppedNote("Trash: ${result.restored} restored, ${result.failures.size} failed", result.cancelled),
    lines = restoreResultLines(result, conflicts),
    failures = result.failures.size,
    stopped = result.cancelled,
)
