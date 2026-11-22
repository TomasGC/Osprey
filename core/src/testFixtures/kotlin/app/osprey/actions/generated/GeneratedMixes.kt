package app.osprey.actions.generated

/**
 * Runs a property over generated mixes (#129). A failure is shrunk and thrown with its seed, so
 * `GeneratedMixes.check(listOf(seed)) { ... }` replays exactly that mix.
 */
object GeneratedMixes {
    const val FIRST_SEED = 129L
    const val ROUNDS = 300

    /** Round n uses seed [FIRST_SEED] + n. */
    val SEEDS: List<Long> = List(ROUNDS) { FIRST_SEED + it }

    fun check(seeds: List<Long> = SEEDS, property: (List<FileSpec>) -> Unit) {
        for (seed in seeds) {
            val mix = MixGenerator.mix(seed)
            if (failureOf(mix, property) == null) continue
            val smallest = shrink(mix) { failureOf(it, property) != null }
            throw AssertionError(report(seed, mix, smallest, failureOf(smallest, property)))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun failureOf(mix: List<FileSpec>, property: (List<FileSpec>) -> Unit): String? = try {
        property(mix)
        null
    } catch (e: AssertionError) {
        e.message ?: "assertion failed"
    } catch (e: Exception) {
        "${e::class.simpleName}: ${e.message}"
    }

    private fun report(seed: Long, mix: List<FileSpec>, smallest: List<FileSpec>, message: String?): String =
        "seed=$seed: $message\nsmallest mix (${smallest.size} of ${mix.size} files), " +
            "replay with GeneratedMixes.check(listOf(${seed}L)):\n" +
            smallest.joinToString("\n") { "  ${it.describe()}" }
}
