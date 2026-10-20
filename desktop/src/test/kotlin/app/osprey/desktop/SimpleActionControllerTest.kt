package app.osprey.desktop

import app.osprey.filesystem.FileHandle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared no-op [FileHandle] stub, reused by other desktop unit tests that don't touch the filesystem. */
object FakeHandle : FileHandle

class SimpleActionControllerTest {

    @Test
    fun `apply before any scan does nothing`() {
        var applyCalls = 0
        val controller = SimpleActionController<Int>(
            scan = { ScanResult(5, "count=5", listOf("a"), 5) },
            apply = { _, plan -> applyCalls++; plan },
        )

        runBlocking { controller.onApply(FakeHandle) }

        assertEquals(0, applyCalls)
        assertFalse(controller.state.applyEnabled)
    }

    @Test
    fun `scan populates state and enables apply when the plan is non-empty`() {
        val controller = SimpleActionController<Int>(
            scan = { ScanResult(5, "count=5", listOf("a", "b"), 5) },
            apply = { _, plan -> plan },
        )

        runBlocking { controller.onScan(FakeHandle) }

        assertEquals("count=5", controller.state.summary)
        assertEquals(listOf("a", "b"), controller.state.lines)
        assertTrue(controller.state.applyEnabled)
    }

    @Test
    fun `scan with an empty plan leaves apply disabled`() {
        val controller = SimpleActionController<Int>(
            scan = { ScanResult(0, "count=0", emptyList(), 0) },
            apply = { _, plan -> plan },
        )

        runBlocking { controller.onScan(FakeHandle) }

        assertFalse(controller.state.applyEnabled)
    }

    @Test
    fun `apply consumes the cached plan exactly once, never re-scanning`() {
        var scanCalls = 0
        var applyCalls = 0
        val controller = SimpleActionController<Int>(
            scan = { scanCalls++; ScanResult(5, "count=5", emptyList(), 5) },
            apply = { _, plan -> applyCalls++; plan },
        )

        runBlocking {
            controller.onScan(FakeHandle)
            controller.onApply(FakeHandle)
            controller.onApply(FakeHandle) // No new scan in between: must be a no-op.
        }

        assertEquals(1, scanCalls)
        assertEquals(1, applyCalls)
        assertFalse(controller.state.applyEnabled)
        assertEquals("  -- APPLIED (5)", controller.state.appliedSuffix)
    }

    @Test
    fun `a fresh scan clears the previous applied suffix`() {
        val controller = SimpleActionController<Int>(
            scan = { ScanResult(5, "count=5", emptyList(), 5) },
            apply = { _, plan -> plan },
        )

        runBlocking {
            controller.onScan(FakeHandle)
            controller.onApply(FakeHandle)
            controller.onScan(FakeHandle)
        }

        assertEquals("", controller.state.appliedSuffix)
    }
}
