package app.osprey.actions.reorg

import app.osprey.filesystem.FakeFileSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorgReportTest {

    private val handle = FakeFileSystemRepository().root

    private fun item(operation: ReorgOperation) = ReorgItem(handle, "x", 1, operation, 2018, "x.jpg", "")

    @Test
    fun `summary counts each operation and each kind of skipped file`() {
        val plan = ReorgPlan(
            items = listOf(item(ReorgOperation.COPY), item(ReorgOperation.COPY), item(ReorgOperation.MOVE)),
            alreadyInOutput = listOf("a"),
            undated = listOf("b", "c"),
            unreachable = listOf("d"),
        )

        assertEquals(
            "count=3  copy=2  move=1  refile=0  already_in_output=1  undated=2  unreachable=1",
            reorgSummary(plan),
        )
    }

    @Test
    fun `result lines list each failure with its reason`() {
        val result = ReorgResult(1, 2, 3, listOf(ReorgFailure("Camera/a.jpg", "locked")), cancelled = false)

        val lines = reorgResultLines(result)

        assertEquals("  APPLIED copied=1 moved=2 refiled=3 failed=1", lines.first())
        assertTrue(lines.contains("  FAILED Camera/a.jpg: locked (source kept)"))
    }

    @Test
    fun `result lines say when the run was stopped early`() {
        val lines = reorgResultLines(ReorgResult(0, 0, 0, emptyList(), cancelled = true))

        assertTrue(lines.any { it.contains("STOPPED") })
    }
}
