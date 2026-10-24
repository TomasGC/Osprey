package app.osprey.jobs

import kotlinx.coroutines.Deferred

/** What the caller of [JobQueue.enqueue] keeps: wait for the outcome, or ask the job to stop. */
class JobHandle internal constructor(
    val id: Long,
    private val outcome: Deferred<JobOutcome>,
    private val requestStop: () -> Unit,
) {

    /** Suspends until the job is over, which may be long after it was queued. */
    suspend fun await(): JobOutcome = outcome.await()

    /** Removes the job if it is still waiting; otherwise asks it to stop after the file it is on. */
    fun stop() = requestStop()
}
