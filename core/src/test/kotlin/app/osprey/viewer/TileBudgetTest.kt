package app.osprey.viewer

import app.osprey.media.PhotoRegion
import org.junit.Assert.assertEquals
import org.junit.Test

private const val ONE_TILE = 512L * 512 * 4

class TileBudgetTest {

    private fun tile(column: Int) = DetailTile(PhotoRegion(column * 512, 0, 512, 512), 1)

    private val a = tile(0)
    private val b = tile(1)
    private val c = tile(2)

    @Test
    fun `a tile holds its region divided by its sample size, in four bytes a pixel`() {
        assertEquals(ONE_TILE, a.bytes())
        assertEquals(ONE_TILE, DetailTile(PhotoRegion(0, 0, 1024, 1024), 2).bytes())
        assertEquals(123L * 73 * 4, DetailTile(PhotoRegion(0, 0, 490, 290), 4).bytes())
    }

    @Test
    fun `everything missing is loaded, in plan order, when it fits`() =
        assertEquals(listOf(a, b, c), missingWithinBudget(listOf(a, b, c), emptySet(), ONE_TILE * 3))

    @Test
    fun `loading stops at the first tile that does not fit`() =
        assertEquals(listOf(a, b), missingWithinBudget(listOf(a, b, c), emptySet(), ONE_TILE * 2 + 1))

    @Test
    fun `tiles already held use up part of the budget and are not loaded again`() =
        assertEquals(listOf(b), missingWithinBudget(listOf(a, b, c), setOf(a), ONE_TILE * 2))

    @Test
    fun `a held tile outside the plan costs nothing`() =
        assertEquals(listOf(a), missingWithinBudget(listOf(a, b), setOf(c), ONE_TILE + 1))

    @Test
    fun `nothing is missing when everything is held`() =
        assertEquals(emptyList<DetailTile>(), missingWithinBudget(listOf(a, b), setOf(a, b), ONE_TILE * 2))
}
