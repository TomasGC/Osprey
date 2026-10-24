package app.osprey.actions.reorg

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorgResultTest {

    @Test
    fun `done counts every file that was copied, moved or refiled`() {
        val result = ReorgResult(
            copied = 3,
            moved = 2,
            refiled = 1,
            failures = listOf(ReorgFailure("Camera/a.jpg", "locked")),
            cancelled = false,
        )

        assertEquals(6, result.done)
    }

    @Test
    fun `a run that did nothing has done zero`() {
        assertEquals(0, ReorgResult(0, 0, 0, emptyList(), cancelled = true).done)
    }
}
