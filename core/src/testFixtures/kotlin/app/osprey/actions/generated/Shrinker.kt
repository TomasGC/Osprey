package app.osprey.actions.generated

/**
 * The smallest mix for which [fails] still holds, found by dropping one file at a time, then clearing one file's
 * metadata at a time, until neither helps. Greedy, so small rather than provably minimal.
 */
fun shrink(mix: List<FileSpec>, fails: (List<FileSpec>) -> Boolean): List<FileSpec> {
    var current = mix
    while (true) {
        current = smallerMixes(current).firstOrNull(fails) ?: return current
    }
}

private fun smallerMixes(mix: List<FileSpec>): Sequence<List<FileSpec>> {
    val withoutOne = mix.indices.asSequence().map { drop -> mix.filterIndexed { i, _ -> i != drop } }
    val withoutMetadata = mix.indices.asSequence()
        .filter { mix[it].metadata != SpecMetadata.None }
        .map { clear -> mix.mapIndexed { i, spec -> if (i == clear) spec.withoutMetadata() else spec } }
    return (withoutOne + withoutMetadata).filter { it.isNotEmpty() }
}

private fun FileSpec.withoutMetadata(): FileSpec = copy(metadata = SpecMetadata.None)
