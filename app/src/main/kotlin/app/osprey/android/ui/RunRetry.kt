package app.osprey.android.ui

import app.osprey.history.RunRecord

/**
 * What the History screen needs to offer Retry failed on a run's page (#83): whether [canRetry] the run, the
 * [question] to ask before an irreversible retry (null when none is needed) and what [onRetry] does.
 */
class RunRetry(
    val canRetry: (RunRecord) -> Boolean,
    val question: (RunRecord) -> String?,
    val onRetry: (RunRecord) -> Unit,
)
