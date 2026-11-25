package app.osprey.actions

/**
 * The names a folder holds and the ones a plan has claimed, compared ignoring case: many file systems take two names
 * that differ only by case for one (#132).
 */
internal class TakenNames(existing: Collection<String> = emptyList()) {

    private val existingCounts = existing.groupingBy { it.lowercase() }.eachCount()
    private val claimed = mutableSetOf<String>()

    fun claim(name: String) {
        claimed += name.lowercase()
    }

    operator fun contains(name: String): Boolean = name.lowercase().let { it in claimed || it in existingCounts }

    /** Whether [candidate] belongs to another file than the one called [ownName], which may change its own case. */
    fun takenByAnother(candidate: String, ownName: String): Boolean {
        val key = candidate.lowercase()
        val ownEntries = if (ownName.lowercase() == key) 1 else 0
        return key in claimed || existingCounts.getOrDefault(key, 0) > ownEntries
    }
}
