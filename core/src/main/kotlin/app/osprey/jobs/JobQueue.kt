package app.osprey.jobs

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val FINISHED_KEPT = 5

/**
 * Runs jobs one after the other on [scope]'s dispatcher. [enqueue] returns at once; [entries] is what a
 * screen or a notification watches. A job is plain blocking code that takes a [JobControl]: it reports the
 * file it is on and checks [JobControl.shouldStop] between two files. Nothing here knows about Android or
 * a desktop window, so the same queue backs both.
 */
class JobQueue(scope: CoroutineScope) {

    private class Pending(
        val id: Long,
        val name: String,
        val work: (JobControl) -> JobOutcome,
        val onDone: (JobOutcome) -> Unit,
    ) {
        val outcome = CompletableDeferred<JobOutcome>()
        val stopRequested = AtomicBoolean(false)
        var removed = false
    }

    private val lock = Any()
    private val ids = AtomicLong(1L)
    private val waiting = Channel<Pending>(Channel.UNLIMITED)
    private val active = mutableMapOf<Long, Pending>()
    private val mutableEntries = MutableStateFlow<List<JobEntry>>(emptyList())

    /** Every queued, running and recently finished job, oldest first. */
    val entries: StateFlow<List<JobEntry>> = mutableEntries

    init {
        // Runs until the scope is cancelled; the channel is never closed, so there is no last job to wait for.
        scope.launch { while (true) run(waiting.receive()) }
    }

    /**
     * Queues [work] under [name]; it starts once the jobs before it are over. [onDone] is told the outcome
     * as soon as the job is over, also when it was removed before it started, whatever screen is showing.
     */
    fun enqueue(name: String, onDone: (JobOutcome) -> Unit = {}, work: (JobControl) -> JobOutcome): JobHandle {
        val job = Pending(ids.getAndIncrement(), name, work, onDone)
        synchronized(lock) {
            active[job.id] = job
            mutableEntries.value = mutableEntries.value + JobEntry(job.id, name, JobState.Queued)
        }
        waiting.trySend(job)
        return JobHandle(job.id, job.outcome) { stop(job.id) }
    }

    /** Forgets a finished job; a job that is waiting or running stays. */
    fun dismiss(id: Long) {
        synchronized(lock) {
            mutableEntries.value = mutableEntries.value.filterNot { it.id == id && it.state is JobState.Finished }
        }
    }

    private fun run(job: Pending) {
        val started = synchronized(lock) {
            if (!job.removed) setState(job.id, JobState.Running())
            !job.removed
        }
        if (!started) return
        val outcome = execute(job)
        synchronized(lock) {
            active.remove(job.id)
            setState(job.id, JobState.Finished(outcome))
            keepLastFinished()
        }
        job.outcome.complete(outcome)
        notifyDone(job, outcome)
    }

    @Suppress("TooGenericExceptionCaught") // A job is arbitrary planner code: whatever it throws ends that job only.
    private fun execute(job: Pending): JobOutcome = try {
        job.work(Control(job))
    } catch (e: Exception) {
        JobOutcome(summary = "${job.name} failed: ${e.message ?: e::class.simpleName}", failures = 1)
    }

    /** Removes the job if it is still waiting; otherwise asks it to stop after the file it is on. */
    fun stop(id: Long) {
        val removed = synchronized(lock) { active[id]?.takeIf { stopLocked(it) } } ?: return
        val outcome = JobOutcome("${removed.name}: stopped before it started", stopped = true)
        removed.outcome.complete(outcome)
        notifyDone(removed, outcome)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // A watcher that fails must not end the queue.
    private fun notifyDone(job: Pending, outcome: JobOutcome) {
        try {
            job.onDone(outcome)
        } catch (ignored: Exception) {
            // The job is over and its outcome delivered; there is nothing left to undo.
        }
    }

    /** Marks [job] as asked to stop; true when it was still waiting and is gone from the queue. */
    private fun stopLocked(job: Pending): Boolean {
        job.stopRequested.set(true)
        val state = mutableEntries.value.first { it.id == job.id }.state
        if (state == JobState.Queued) {
            job.removed = true
            active.remove(job.id)
            mutableEntries.value = mutableEntries.value.filterNot { it.id == job.id }
            return true
        }
        update(job.id) { (it as JobState.Running).copy(stopping = true) }
        return false
    }

    private fun setState(id: Long, state: JobState) = update(id) { state }

    private fun update(id: Long, change: (JobState) -> JobState) {
        mutableEntries.value = mutableEntries.value.map { if (it.id == id) it.copy(state = change(it.state)) else it }
    }

    private fun keepLastFinished() {
        val finished = mutableEntries.value.filter { it.state is JobState.Finished }
        val dropped = finished.dropLast(FINISHED_KEPT).map { it.id }.toSet()
        mutableEntries.value = mutableEntries.value.filterNot { it.id in dropped }
    }

    private inner class Control(private val job: Pending) : JobControl {

        override fun shouldStop(): Boolean = job.stopRequested.get()

        override fun report(done: Int, total: Int, current: String) {
            val progress = JobProgress(done, total, current)
            synchronized(lock) {
                update(job.id) { JobState.Running(progress, stopping = job.stopRequested.get()) }
            }
        }
    }
}
