package app.osprey.actions.dates

import app.osprey.actions.filenameDateTime
import app.osprey.actions.splitStemAndSuffix
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.MediaMetadataReader

/**
 * Resolves the best-known capture datetime for every file directly inside `root`: EXIF/video-
 * container metadata first (via [MediaMetadataReader]), a date parsed from the filename
 * otherwise. A straight re-platform of the POC's `plan_dates`/`describe_dates` — applying the
 * resolved date to the actual filesystem (including Windows' separate Created-time) is
 * explicitly deferred to `:desktop`/`:android`'s real `FileSystemRepository.writeTimestamps`.
 */
class DatesPlanner(
    private val fs: FileSystemRepository,
    private val mediaMetadataReader: MediaMetadataReader,
) {

    /**
     * Scans `root`'s immediate files — `TODELETE` and other subfolders are never touched, since
     * they simply aren't files. A file resolves via [MediaMetadataReader.captureDateTime] (EXIF
     * for images, video-container metadata for videos) first, [filenameDateTime] otherwise; one
     * that resolves neither way ends up in [DatesPlan.unfixable].
     */
    fun plan(root: FileHandle): DatesPlan {
        val fixes = mutableListOf<DatesFix>()
        val unfixable = mutableListOf<FileEntry>()
        for (entry in fs.list(root).filterNot { it.isDirectory }) {
            val resolved = resolveDate(entry)
            if (resolved == null) {
                unfixable += entry
            } else {
                fixes += DatesFix(entry.handle, entry.name, resolved.dateTime, resolved.source)
            }
        }
        return DatesPlan(fixes, unfixable)
    }

    /**
     * Logs an `UNFIXABLE` line per unresolved file, then a `SET` line per fix.
     * Returns (fixed-from-metadata count, fixed-from-filename count), matching the POC's summary split.
     */
    fun describe(plan: DatesPlan, log: (String) -> Unit): Pair<Int, Int> {
        for (entry in plan.unfixable) {
            log("  UNFIXABLE ${entry.name}")
        }
        for (fix in plan.fixes) {
            log("  SET ${fix.name}  ->  ${fix.dateTime}  (from ${displayName(fix.source)})")
        }
        val fixedFromMetadata = plan.fixes.count { it.source != MediaDateSource.FILENAME }
        val fixedFromFilename = plan.fixes.count { it.source == MediaDateSource.FILENAME }
        return fixedFromMetadata to fixedFromFilename
    }

    private fun resolveDate(entry: FileEntry): MediaDate? =
        mediaMetadataReader.captureDateTime(entry.handle)
            ?: filenameFallback(entry.name)

    private fun filenameFallback(name: String): MediaDate? {
        val stem = splitStemAndSuffix(name).first
        val dateTime = filenameDateTime(stem) ?: return null
        return MediaDate(dateTime, MediaDateSource.FILENAME)
    }

    private fun displayName(source: MediaDateSource): String = when (source) {
        MediaDateSource.EXIF -> "EXIF"
        MediaDateSource.VIDEO_METADATA -> "VIDEO-META"
        MediaDateSource.FILENAME -> "FILENAME"
    }
}
