package app.osprey.android

import app.osprey.history.InMemoryJobHistoryStore
import app.osprey.history.JobHistoryStore
import app.osprey.history.RunRecorder
import app.osprey.jobs.JobQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * A queue that runs each job on the calling thread, so a test sees the result as soon as it enqueues. Its runs go to
 * [history], which a test can read to see what a job recorded.
 */
fun immediateJobQueue(history: JobHistoryStore = InMemoryJobHistoryStore()) =
    JobQueue(CoroutineScope(Dispatchers.Unconfined), RunRecorder(history))
