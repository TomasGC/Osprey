package app.osprey.jobs

/**
 * How a job ended.
 *
 * @property summary One line for a notification or a banner, e.g. `Reorg: 12 done, 1 failed`.
 * @property lines The detail, one line per file or finding, for the action's own report.
 * @property failures How many files could not be handled; each is still where it was.
 * @property stopped True when the job ended early because it was asked to.
 */
data class JobOutcome(
    val summary: String,
    val lines: List<String> = emptyList(),
    val failures: Int = 0,
    val stopped: Boolean = false,
) {

    /** True when every file was handled and nothing stopped the job. */
    val succeeded: Boolean get() = failures == 0 && !stopped
}
