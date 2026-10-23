package app.osprey.settings

/**
 * Where a folder sits, as a volume plus the path segments below it, so two folders can be compared
 * without a platform path type: desktop builds one from a `Path`, Android from a tree document id
 * (`primary:Pictures/WhatsApp`). Build one with [of], which normalizes it; comparison ignores case,
 * the safe side of a rare false overlap on a case-sensitive disk.
 */
data class FolderLocation(val volume: String, val segments: List<String>) {

    /** True if [other] is this folder or sits anywhere below it, on the same volume. */
    fun contains(other: FolderLocation): Boolean =
        volume == other.volume && other.segments.take(segments.size) == segments

    /** True if one of the two folders contains the other. */
    fun overlaps(other: FolderLocation): Boolean = contains(other) || other.contains(this)

    companion object {
        /** [path] split on `/` and `\`, empty and `.` segments dropped, everything lower-cased. */
        fun of(volume: String, path: String): FolderLocation = FolderLocation(
            volume = volume.lowercase(),
            segments = path.split('/', '\\').filter { it.isNotEmpty() && it != "." }.map { it.lowercase() },
        )
    }
}
