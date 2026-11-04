package app.osprey.history

import java.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val T0: Instant = Instant.parse("2026-11-03T10:00:00Z")

class HistoryControllerTest {

    private val store = InMemoryJobHistoryStore()

    private fun ended(action: String): Long =
        store.begin(action, T0).also { store.finish(it, RunState.Done, "$action done", T0) }

    private fun actions(controller: HistoryController) =
        controller.view.value.sections.flatMap { it.runs }.map { it.action }

    @Test
    fun `it starts with the runs of the store in sections and nothing open`() {
        ended("Old")
        store.begin("Waiting", T0)

        val view = HistoryController(store).view.value

        assertEquals(listOf(RunState.Queued, RunState.Done), view.sections.map { it.state })
        assertNull(view.selected)
    }

    @Test
    fun `refresh shows a run that began after the controller was built`() {
        val controller = HistoryController(store)
        store.begin("Late", T0)
        assertEquals(emptyList<String>(), actions(controller))

        controller.refresh()

        assertEquals(listOf("Late"), actions(controller))
    }

    @Test
    fun `an open run is selected and its page follows the run`() {
        val id = store.begin("Reorg", T0)
        val controller = HistoryController(store)

        controller.open(id)
        assertEquals(RunState.Queued, controller.view.value.selected?.state)
        store.finish(id, RunState.Failed, "Reorg failed", T0)
        controller.refresh()

        assertEquals(RunState.Failed, controller.view.value.selected?.state)
    }

    @Test
    fun `the ended runs are counted, a waiting or running one is not`() {
        ended("Done")
        ended("Also done")
        store.begin("Failed", T0).also { store.finish(it, RunState.Failed, "x", T0) }
        store.begin("Stopped", T0).also { store.finish(it, RunState.Stopped, "x", T0) }
        store.begin("Waiting", T0)
        store.begin("Running", T0).also { store.start(it, T0) }

        assertEquals(4, HistoryController(store).view.value.endedCount)
    }

    @Test
    fun `an empty history, or one that has only active runs, has none ended`() {
        assertEquals(0, HistoryController(store).view.value.endedCount)
        store.begin("Waiting", T0)

        assertEquals(0, HistoryController(store).view.value.endedCount)
    }

    @Test
    fun `back closes the page`() {
        val id = ended("Reorg")
        val controller = HistoryController(store)
        controller.open(id)

        controller.back()

        assertNull(controller.view.value.selected)
    }

    @Test
    fun `opening a run that does not exist opens nothing`() {
        val controller = HistoryController(store)

        controller.open(99)

        assertNull(controller.view.value.selected)
    }

    @Test
    fun `a run that disappears closes its page for good`() {
        val id = ended("Old")
        val controller = HistoryController(store)
        controller.open(id)

        store.clear()
        controller.refresh()
        assertNull(controller.view.value.selected)
        store.begin("New", T0)
        controller.refresh()

        assertNull(controller.view.value.selected)
    }

    @Test
    fun `clear forgets the ended runs and keeps an active one`() {
        ended("Old")
        store.begin("Waiting", T0)
        val controller = HistoryController(store)

        controller.clear()

        assertEquals(listOf("Waiting"), actions(controller))
    }

    @Test
    fun `the view is empty only when there is no run`() {
        val controller = HistoryController(store)
        assertTrue(controller.view.value.isEmpty)

        store.begin("A", T0)
        controller.refresh()

        assertFalse(controller.view.value.isEmpty)
    }

    @Test
    fun `follow reads the store again at most once per interval`() = runTest {
        val counting = CountingStore(store)
        val controller = HistoryController(counting)
        backgroundScope.launch { controller.follow(100) }
        runCurrent()
        assertEquals(2, counting.reads)

        val id = counting.begin("A", T0)
        runCurrent()
        counting.appendLog(id, "x")
        runCurrent()
        counting.start(id, T0)
        runCurrent()
        assertEquals(2, counting.reads)

        advanceTimeBy(101)
        runCurrent()

        assertEquals(3, counting.reads)
        assertEquals(RunState.Running, controller.view.value.sections.single().runs.single().state)
    }

    @Test
    fun `follow waits half a second by default`() = runTest {
        val counting = CountingStore(store)
        val controller = HistoryController(counting)
        backgroundScope.launch { controller.follow() }
        runCurrent()
        counting.begin("A", T0)
        runCurrent()
        assertEquals(2, counting.reads)

        advanceTimeBy(499)
        runCurrent()
        assertEquals(2, counting.reads)
        advanceTimeBy(2)
        runCurrent()

        assertEquals(3, counting.reads)
    }

    /** A store that counts how many times the runs were read. */
    private class CountingStore(private val inner: JobHistoryStore) : JobHistoryStore by inner {
        var reads = 0

        override fun runs(): List<RunRecord> {
            reads++
            return inner.runs()
        }
    }
}
