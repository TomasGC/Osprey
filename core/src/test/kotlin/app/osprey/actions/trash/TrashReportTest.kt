package app.osprey.actions.trash

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashReportTest {

    @Test
    fun `a clean clear is one line`() {
        val result = TrashClearResult(deleted = 3, failures = emptyList(), prunedFolders = 2, cancelled = false)
        val lines = clearResultLines(result)

        assertEquals(listOf("  DELETED 3, failed 0, empty folders removed 2"), lines)
    }

    @Test
    fun `a stopped clear with a failure says so and names the file`() {
        val result = TrashClearResult(1, listOf(TrashFailure("Trash/2019/Dedupe/a.jpg", "locked")), 0, cancelled = true)

        assertEquals(
            listOf(
                "  DELETED 1, failed 1, empty folders removed 0",
                "  STOPPED before the end, as asked",
                "  FAILED Trash/2019/Dedupe/a.jpg: locked (still in the Trash)",
            ),
            clearResultLines(result),
        )
    }

    @Test
    fun `a restore counts the conflicts left to settle`() {
        val result = TrashRestoreResult(restored = 4, failures = emptyList(), prunedFolders = 1, cancelled = false)

        assertEquals(
            listOf("  RESTORED 4, failed 0, to settle 2, empty folders removed 1"),
            restoreResultLines(result, conflicts = 2),
        )
    }

    @Test
    fun `a stopped restore with a failure says so and names the file`() {
        val result = TrashRestoreResult(0, listOf(TrashFailure("Trash/2019/Dedupe/a.jpg", "name taken")), 0, true)

        assertEquals(
            listOf(
                "  RESTORED 0, failed 1, to settle 0, empty folders removed 0",
                "  STOPPED before the end, as asked",
                "  FAILED Trash/2019/Dedupe/a.jpg: name taken (still in the Trash)",
            ),
            restoreResultLines(result, conflicts = 0),
        )
    }
}
