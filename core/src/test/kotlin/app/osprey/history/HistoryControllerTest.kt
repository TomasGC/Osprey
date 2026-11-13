package app.osprey.history

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryControllerTest {

    private val store = InMemoryJobHistoryStore()

    private fun ended(action: String): Long =
        store.begin(action, T0).also { store.finish(it, RunState.Done, "$action done", T0) }

    private fun finished(action: String, state: RunState): Long =
        store.begin(action, T0).also { store.finish(it, state, "$action ${state.name}", T0) }

    private fun actions(controller: HistoryController) =
        controller.view.value.sections.flatMap { it.runs }.map { it.action }

    private fun stored() = store.runs().map { it.action }.sorted()

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

        store.remove(setOf(id))
        controller.refresh()
        assertNull(controller.view.value.selected)
        store.begin("New", T0)
        controller.refresh()

        assertNull(controller.view.value.selected)
    }

    @Test
    fun `a tap opens a run while nothing is checked`() {
        val id = ended("Old")
        val controller = HistoryController(store)

        controller.press(id)

        assertEquals(id, controller.view.value.selected?.id)
        assertEquals(emptySet<Long>(), controller.view.value.checked)
    }

    @Test
    fun `a long press checks a run and the next tap toggles instead of opening`() {
        val first = ended("First")
        val second = ended("Second")
        val controller = HistoryController(store)

        controller.longPress(first)
        controller.press(second)

        assertEquals(setOf(first, second), controller.view.value.checked)
        assertNull(controller.view.value.selected)
        controller.press(first)
        assertEquals(setOf(second), controller.view.value.checked)
        controller.longPress(second)
        assertEquals(emptySet<Long>(), controller.view.value.checked)
    }

    @Test
    fun `a waiting or running run cannot be checked`() {
        val waiting = store.begin("Waiting", T0)
        val running = store.begin("Running", T0).also { store.start(it, T0) }
        val done = ended("Done")
        val controller = HistoryController(store)

        controller.longPress(waiting)
        controller.longPress(running)
        controller.longPress(99L)
        assertFalse(controller.view.value.selecting)
        controller.longPress(done)
        controller.press(waiting)

        assertEquals(setOf(done), controller.view.value.checked)
    }

    @Test
    fun `unchecking the last run ends the selection and back clears the checks`() {
        val first = ended("First")
        val second = ended("Second")
        val controller = HistoryController(store)
        controller.longPress(first)
        assertTrue(controller.view.value.selecting)
        controller.press(first)
        assertFalse(controller.view.value.selecting)

        controller.longPress(first)
        controller.press(second)
        controller.back()

        assertFalse(controller.view.value.selecting)
        assertEquals(emptySet<Long>(), controller.view.value.checked)
    }

    @Test
    fun `the button reads Clear successful, enabled only while a successful run exists`() {
        finished("Failed", RunState.Failed)
        finished("Stopped", RunState.Stopped)
        finished("Interrupted", RunState.Interrupted)
        store.begin("Waiting", T0)
        val controller = HistoryController(store)
        assertEquals("Clear successful", controller.view.value.clearLabel)
        assertFalse(controller.view.value.clearEnabled)

        ended("Done")
        controller.refresh()

        assertTrue(controller.view.value.clearEnabled)
    }

    @Test
    fun `the button reads Clear selected with the count while selecting`() {
        val first = ended("First")
        val second = ended("Second")
        val controller = HistoryController(store)

        controller.longPress(first)
        assertEquals("Clear selected (1)", controller.view.value.clearLabel)
        controller.press(second)

        assertEquals("Clear selected (2)", controller.view.value.clearLabel)
        assertTrue(controller.view.value.clearEnabled)
    }

    @Test
    fun `clear hides the successful runs, keeps every other state, and the store changes only at commit`() {
        ended("Done 1")
        ended("Done 2")
        finished("Failed", RunState.Failed)
        finished("Stopped", RunState.Stopped)
        finished("Interrupted", RunState.Interrupted)
        store.begin("Waiting", T0)
        val controller = HistoryController(store)

        controller.clear()

        assertEquals(listOf("Waiting", "Failed", "Stopped", "Interrupted"), actions(controller))
        assertEquals(2, controller.view.value.removed)
        assertEquals(6, store.runs().size)
        controller.commit()
        assertEquals(listOf("Failed", "Interrupted", "Stopped", "Waiting"), stored())
        assertEquals(0, controller.view.value.removed)
    }

    @Test
    fun `clear with runs checked removes those runs only`() {
        val done = ended("Done")
        val failed = finished("Failed", RunState.Failed)
        ended("Other done")
        val controller = HistoryController(store)
        controller.longPress(done)
        controller.press(failed)

        controller.clear()

        assertEquals(listOf("Other done"), actions(controller))
        assertFalse(controller.view.value.selecting)
        controller.commit()
        assertEquals(listOf("Other done"), stored())
    }

    @Test
    fun `clear with nothing to clear does nothing`() {
        finished("Failed", RunState.Failed)
        val controller = HistoryController(store)

        controller.clear()

        assertEquals(0, controller.view.value.removed)
        assertEquals(listOf("Failed"), actions(controller))
    }

    @Test
    fun `the notice says how many runs were removed`() {
        val first = ended("First")
        val second = ended("Second")
        val controller = HistoryController(store)
        assertNull(controller.view.value.removedText)

        controller.remove(setOf(first))
        assertEquals("Run removed", controller.view.value.removedText)
        controller.remove(setOf(second))
        assertEquals("Run removed", controller.view.value.removedText)

        val more = listOf(ended("A"), ended("B"))
        controller.refresh()
        controller.remove(more.toSet())
        assertEquals("2 runs removed", controller.view.value.removedText)
    }

    @Test
    fun `undo brings the removed runs back and the store never lost them`() {
        val first = ended("First")
        val second = ended("Second")
        val controller = HistoryController(store)

        controller.remove(setOf(first, second))
        assertEquals(emptyList<String>(), actions(controller))
        controller.undo()

        assertEquals(listOf("Second", "First"), actions(controller))
        assertEquals(0, controller.view.value.removed)
        assertEquals(listOf("First", "Second"), stored())
    }

    @Test
    fun `a second removal applies the first one`() {
        val first = ended("First")
        val second = ended("Second")
        ended("Third")
        val controller = HistoryController(store)

        controller.remove(setOf(first))
        controller.remove(setOf(second))

        assertEquals(listOf("Second", "Third"), stored())
        assertEquals(listOf("Third"), actions(controller))
        controller.undo()
        assertEquals(listOf("Third", "Second"), actions(controller))
    }

    @Test
    fun `a waiting or running run, or an unknown one, is never removed`() {
        val waiting = store.begin("Waiting", T0)
        val running = store.begin("Running", T0).also { store.start(it, T0) }
        val controller = HistoryController(store)

        controller.remove(setOf(waiting, running, 99L))

        assertEquals(0, controller.view.value.removed)
        assertEquals(listOf("Running", "Waiting"), actions(controller))
    }

    @Test
    fun `removing the open run closes its page`() {
        val id = ended("Old")
        val controller = HistoryController(store)
        controller.open(id)

        controller.remove(setOf(id))

        assertNull(controller.view.value.selected)
        controller.undo()
        assertEquals(id, controller.view.value.selected?.id)
    }

    @Test
    fun `a removed run stays hidden when the screen reads the store again`() {
        val id = ended("Old")
        val controller = HistoryController(store)

        controller.remove(setOf(id))
        controller.refresh()

        assertEquals(emptyList<String>(), actions(controller))
    }

    @Test
    fun `a checked run that the store loses is unchecked`() {
        val id = ended("Old")
        ended("Other")
        val controller = HistoryController(store)
        controller.longPress(id)

        store.remove(setOf(id))
        controller.refresh()

        assertEquals(emptySet<Long>(), controller.view.value.checked)
    }

    @Test
    fun `commit with nothing pending does nothing and undo too`() {
        ended("Old")
        val controller = HistoryController(store)
        val before = store.revision.value

        controller.commit()
        controller.undo()

        assertEquals(before, store.revision.value)
        assertEquals(listOf("Old"), actions(controller))
    }

    @Test
    fun `each removal has its own id so a screen can restart its timer`() {
        val first = ended("First")
        val second = ended("Second")
        val controller = HistoryController(store)
        val none = controller.view.value.removalId

        controller.remove(setOf(first))
        val one = controller.view.value.removalId
        controller.remove(setOf(second))
        val two = controller.view.value.removalId
        controller.undo()

        assertTrue(one != none && two != one)
        assertEquals(two, controller.view.value.removalId)
    }

    @Test
    fun `a removal waits eight seconds for an undo`() {
        assertEquals(8_000L, UNDO_WINDOW_MS)
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
