package app.osprey.actions.generated

import kotlin.random.Random

// Names that differ only by case (#132), made out of a mix without touching what MixGenerator draws.

// Above every id MixGenerator gives, so a twin has content of its own.
private const val TWIN_ID_OFFSET = 101
private const val NAME_HASH_FACTOR = 31

/**
 * The same mix with about half of the extensions in capitals, so files meet under names that differ only by case. Each
 * file decides from its own name and id, so it keeps its name when the shrinker drops others.
 */
fun List<FileSpec>.withUppercaseExtensions(): List<FileSpec> = map {
    val seed = (it.name.hashCode() * NAME_HASH_FACTOR + it.id).toLong()
    if (Random(seed).nextBoolean()) it.withUppercaseExtension() else it
}

/**
 * A second file for each one of the mix: the same name with the extension in capitals and the same metadata, other
 * content. Put in another folder than the first, the two meet when an action gathers them.
 */
fun List<FileSpec>.caseTwins(): List<FileSpec> = map { it.withUppercaseExtension().copy(id = it.id + TWIN_ID_OFFSET) }

private fun FileSpec.withUppercaseExtension(): FileSpec =
    copy(name = "${name.substringBeforeLast('.')}.${name.substringAfterLast('.').uppercase()}")
