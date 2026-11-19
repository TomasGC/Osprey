package app.osprey.actions.reorg

import app.osprey.filesystem.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Test

private fun handle(): FileHandle = object : FileHandle {}

private fun item(label: String, operation: ReorgOperation = ReorgOperation.MOVE) =
    ReorgItem(handle(), label, 1, operation, 2018, label.substringAfterLast('/'), "")

private fun side(label: String) = DuplicateSide(handle(), label, label.substringAfterLast('/'), 1, null, "2018")

private fun pair(input: String, library: String) = ReorgDuplicate("Camera", side(input), side(library))

class ReorgRetryTest {

    private val moved = item("Camera/a.jpg")
    private val refiled = item("2019/b.jpg", ReorgOperation.REFILE)
    private val first = pair("Camera/c.jpg", "2018/c.jpg")
    private val second = pair("Camera/d.jpg", "2018/d.jpg")
    private val plan = ReorgPlan(
        items = listOf(moved, refiled),
        alreadyInOutput = listOf("Camera/x.jpg"),
        duplicates = listOf(first, second),
        undated = listOf("Camera/u.jpg"),
        unreachable = listOf("Gone"),
    )

    @Test
    fun `a reorg plan names its items and both sides of every pair`() {
        assertEquals(
            listOf("Camera/a.jpg", "2019/b.jpg", "Camera/c.jpg", "2018/c.jpg", "Camera/d.jpg", "2018/d.jpg"),
            REORG_RETRY.names(plan),
        )
    }

    @Test
    fun `a reorg retry keeps the named items and the pairs with a failed side, and drops the other lists`() {
        val only = REORG_RETRY.only(plan, setOf("2019/b.jpg", "2018/d.jpg", "Camera/gone.jpg"))

        assertEquals(ReorgPlan(listOf(refiled), emptyList(), listOf(second), emptyList(), emptyList()), only)
    }

    @Test
    fun `a reorg retry keeps a pair whose input side failed`() {
        assertEquals(listOf(first), REORG_RETRY.only(plan, setOf("Camera/c.jpg")).duplicates)
    }

    @Test
    fun `a reorg retry with nothing named keeps nothing`() {
        assertEquals(
            ReorgPlan(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            REORG_RETRY.only(plan, emptySet()),
        )
    }

    @Test
    fun `a pair whose input side failed trashes the input and keeps the library file`() {
        assertEquals(
            listOf(DuplicateChoice(first, keepInput = false, keepLibrary = true)),
            plan.retryChoices(setOf("Camera/c.jpg")),
        )
    }

    @Test
    fun `a pair whose library side failed trashes the library file and keeps the input`() {
        assertEquals(
            listOf(DuplicateChoice(second, keepInput = true, keepLibrary = false)),
            plan.retryChoices(setOf("2018/d.jpg")),
        )
    }

    @Test
    fun `a pair whose two sides failed trashes both, and a pair with no failed side is not listed`() {
        assertEquals(
            listOf(DuplicateChoice(first, keepInput = false, keepLibrary = false)),
            plan.retryChoices(setOf("Camera/c.jpg", "2018/c.jpg", "Camera/a.jpg")),
        )
    }
}
