package app.osprey.actions.visual

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileEntry
import app.osprey.jobs.JobControl
import app.osprey.jobs.NoJobControl
import app.osprey.jobs.RecordingJobControl
import app.osprey.media.FakeMediaMetadataReader
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** How long a test waits for another thread before it fails: nothing here is timed, this only ends a hang. */
private const val WAIT_SECONDS = 10L
private const val TEST_LIMIT_MS = 60_000L

class HashFramesTest {

    private fun files(count: Int): List<FileEntry> {
        val fs = FakeFileSystemRepository()
        repeat(count) { fs.addFile(fs.root, "$it.jpg") }
        return fs.list(fs.root).sortedBy { it.name }
    }

    private fun index(entry: FileEntry): Int = entry.name.removeSuffix(".jpg").toInt()

    private fun CountDownLatch.waitHere() = check(await(WAIT_SECONDS, TimeUnit.SECONDS)) { "waited too long" }

    private fun CyclicBarrier.waitHere() {
        await(WAIT_SECONDS, TimeUnit.SECONDS)
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `several workers give what one gives, in file order, whatever finishes first`() {
        val files = files(6)
        val done = List(files.size) { CountDownLatch(1) }
        val finished = Collections.synchronizedList(mutableListOf<Int>())

        val together = hashFrames(files, 3, NoJobControl) { entry ->
            val i = index(entry)
            // Each file but the third of a run of three waits for the one after it, so the order comes out backwards.
            if (i % 3 != 2) done[i + 1].waitHere()
            finished.add(i)
            done[i].countDown()
            i * 10L
        }

        assertEquals(hashFrames(files, 1, NoJobControl) { index(it) * 10L }, together)
        assertEquals(files.map { it.name }, together!!.map { it.entry.name })
        assertEquals(2, finished.first())
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `as many files as there are workers are hashed at once, never more`() {
        val together = CyclicBarrier(4)
        val running = AtomicInteger()
        val most = AtomicInteger()

        hashFrames(files(8), 4, NoJobControl) {
            most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            together.waitHere()
            running.decrementAndGet()
            1L
        }

        assertEquals(4, most.get())
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `a stop starts nothing more and the result is nothing`() {
        val both = CyclicBarrier(2)
        val hashed = Collections.synchronizedList(mutableListOf<String>())
        val control = RecordingJobControl(stopAfterReports = 2)

        val result = hashFrames(files(6), 2, control) {
            both.waitHere()
            hashed.add(it.name)
            1L
        }

        assertNull(result)
        assertEquals(setOf("0.jpg", "1.jpg"), hashed.toSet())
        assertEquals(2, control.reports.size)
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `a stop asked while files are hashed waits for them before it returns`() {
        val stopped = AtomicBoolean(false)
        val both = CyclicBarrier(2)
        val ended = AtomicInteger()
        val started = AtomicInteger()
        val control = object : JobControl by NoJobControl {
            override fun shouldStop() = stopped.get()
        }

        val result = hashFrames(files(6), 2, control) {
            started.incrementAndGet()
            both.waitHere()
            stopped.set(true)
            ended.incrementAndGet()
            1L
        }

        assertNull(result)
        assertEquals(2, started.get())
        assertEquals(2, ended.get())
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `a stop that comes after the last file started keeps the result`() {
        val control = RecordingJobControl(stopAfterReports = 2)

        val result = hashFrames(files(2), 2, control) { 1L }

        assertEquals(listOf("0.jpg", "1.jpg"), result?.map { it.entry.name })
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `progress is told once per file, with how many are finished, from the calling thread`() {
        val caller = Thread.currentThread()
        val threads = Collections.synchronizedSet(mutableSetOf<Thread>())
        val recording = RecordingJobControl()
        val control = object : JobControl by recording {
            override fun shouldStop(): Boolean {
                threads.add(Thread.currentThread())
                return false
            }

            override fun report(done: Int, total: Int, current: String) {
                threads.add(Thread.currentThread())
                recording.report(done, total, current)
            }
        }
        val together = CyclicBarrier(2)

        hashFrames(files(4), 2, control) {
            together.waitHere()
            1L
        }

        assertEquals(setOf(caller), threads)
        assertEquals(listOf("0.jpg", "1.jpg", "2.jpg", "3.jpg"), recording.reports.map { it.current })
        assertEquals(setOf(4), recording.reports.map { it.total }.toSet())
        val done = recording.reports.map { it.done }
        assertEquals(listOf(0, 0), done.take(2))
        assertEquals(done.sorted(), done)
        // A file starts only once another has finished, so file number n has at least n - 1 finished before it.
        assertEquals(true, done.withIndex().all { (i, finished) -> finished in (i - 1)..i })
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `the first failure in file order is thrown, after what runs has ended`() {
        val thirdFailed = CountDownLatch(1)
        val ended = AtomicInteger()
        val started = Collections.synchronizedList(mutableListOf<Int>())
        val together = CyclicBarrier(4)

        val thrown = assertThrows(IllegalStateException::class.java) {
            hashFrames(files(4), 4, NoJobControl) { entry ->
                val i = index(entry)
                started.add(i)
                together.waitHere()
                try {
                    when (i) {
                        3 -> {
                            thirdFailed.countDown()
                            error("file 3")
                        }
                        1 -> {
                            thirdFailed.waitHere()
                            error("file 1")
                        }
                        else -> 1L
                    }
                } finally {
                    ended.incrementAndGet()
                }
            }
        }

        assertEquals("file 1", thrown.message)
        assertEquals(started.size, ended.get())
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `nothing starts after a failure`() {
        val started = Collections.synchronizedList(mutableListOf<Int>())

        assertThrows(IllegalStateException::class.java) {
            // Every file fails, so whichever of the first two ends first, no third may start.
            hashFrames(files(8), 2, NoJobControl) { entry ->
                started.add(index(entry))
                error("file ${index(entry)}")
            }
        }

        assertEquals(setOf(0, 1), started.toSet())
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `a file with no hash is left out`() {
        for (workers in listOf(1, 3)) {
            val result = hashFrames(files(4), workers, NoJobControl) { if (index(it) % 2 == 0) null else 1L }

            assertEquals("$workers", listOf("1.jpg", "3.jpg"), result?.map { it.entry.name })
        }
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `no file gives an empty result`() {
        assertEquals(emptyList<HashedFile>(), hashFrames(emptyList(), 4, NoJobControl) { 1L })
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `the threads it started end with it`() {
        val threads = Collections.synchronizedSet(mutableSetOf<Thread>())
        val together = CyclicBarrier(3)

        hashFrames(files(3), 3, NoJobControl) {
            threads.add(Thread.currentThread())
            together.waitHere()
            1L
        }

        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)) }
        assertEquals(3, threads.size)
        assertEquals(emptyList<Thread>(), threads.filter { it.isAlive })
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `one worker hashes on the calling thread`() {
        val threads = mutableSetOf<Thread>()

        hashFrames(files(3), 1, NoJobControl) {
            threads.add(Thread.currentThread())
            1L
        }

        assertEquals(setOf(Thread.currentThread()), threads)
    }

    @Test(timeout = TEST_LIMIT_MS)
    fun `a planner with several workers plans what one plans`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        listOf(1, 1, 2, 2, 3, 1).forEachIndexed { i, seed ->
            reader.setFrame(fs.addFile(fs.root, "$i.jpg", ByteArray(100 + i)), noisePixelBuffer(seed = seed))
        }
        // The fake notes the sizes asked for in a plain list: only its frames are read from several threads here.
        val threadSafe = object : app.osprey.media.MediaMetadataReader by reader {
            override fun decodeFrame(entry: app.osprey.filesystem.FileHandle, maxSide: Int?) =
                synchronized(reader) { reader.decodeFrame(entry, maxSide) }
        }

        val together = VisualDupePlanner(fs, threadSafe, workers = 4).plan(fs.root)

        assertEquals(VisualDupePlanner(fs, reader).plan(fs.root), together)
        assertEquals(2, together.groups.size)
    }
}
