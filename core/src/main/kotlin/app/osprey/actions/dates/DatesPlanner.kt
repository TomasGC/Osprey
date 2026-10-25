package app.osprey.actions.dates

import app.osprey.actions.displayName
import app.osprey.actions.resolveMediaDate
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.jobs.JobControl
import app.osprey.jobs.NoJobControl
import app.osprey.jobs.forEachUntilStopped
import app.osprey.media.MediaDateSource
import app.osprey.media.MediaMetadataReader
import java.time.ZoneId

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
     * they simply aren't files. Each file's date is resolved via [resolveMediaDate]; one that
     * resolves neither way ends up in [DatesPlan.unfixable].
     */
    fun plan(root: FileHandle, control: JobControl = NoJobControl): DatesPlan {
        val fixes = mutableListOf<DatesFix>()
        val unfixable = mutableListOf<FileEntry>()
        control.forEachUntilStopped(fs.list(root).filterNot { it.isDirectory }, FileEntry::name) { entry ->
            val resolved = resolveMediaDate(entry.handle, entry.name, mediaMetadataReader)
            if (resolved == null) {
                unfixable += entry
            } else {
                fixes += DatesFix(entry.handle, entry.name, resolved.dateTime, resolved.source)
            }
        }
        return DatesPlan(fixes, unfixable)
    }

    /**
     * Sets the created and modified time of every file in [plan] to its resolved date, read as local time in
     * [zone], and returns how many it set. [control] can stop it between two files. A platform that cannot write
     * timestamps (Android's file system) ignores the call, so the count is then the files it asked about.
     */
    fun apply(plan: DatesPlan, zone: ZoneId = ZoneId.systemDefault(), control: JobControl = NoJobControl): Int {
        var fixed = 0
        control.forEachUntilStopped(plan.fixes, DatesFix::name) { fix ->
            val instant = fix.dateTime.atZone(zone).toInstant()
            fs.writeTimestamps(fix.entry, FileTimestamps(created = instant, modified = instant))
            fixed++
        }
        return fixed
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
}
