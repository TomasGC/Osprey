package app.osprey.android

import app.osprey.jobs.JobQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** A queue that runs each job on the calling thread, so a test sees the result as soon as it enqueues. */
fun immediateJobQueue() = JobQueue(CoroutineScope(Dispatchers.Unconfined))
