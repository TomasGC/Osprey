package app.osprey.viewer

/** Bytes one decoded pixel takes in a bitmap (ARGB). */
const val TILE_BYTES_PER_PIXEL = 4

/** The bytes this tile's bitmap holds once decoded: its region divided by its sample size, rounded up. */
fun DetailTile.bytes(): Long {
    val width = (region.width + sampleSize - 1) / sampleSize
    val height = (region.height + sampleSize - 1) / sampleSize
    return width.toLong() * height * TILE_BYTES_PER_PIXEL
}

/**
 * The tiles of [plan] to decode next: those not in [held], in plan order, while what is held for the plan plus what is
 * added stays within [budgetBytes]. It stops at the first tile that does not fit, so a budget cut only ever drops the
 * tail of the plan (the margin). Held tiles outside the plan are about to be dropped and count for nothing (#60).
 */
fun missingWithinBudget(plan: List<DetailTile>, held: Set<DetailTile>, budgetBytes: Long): List<DetailTile> {
    var total = plan.filter { it in held }.sumOf { it.bytes() }
    return plan.filter { it !in held }.takeWhile { tile ->
        total += tile.bytes()
        total <= budgetBytes
    }
}
