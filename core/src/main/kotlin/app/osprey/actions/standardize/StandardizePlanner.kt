package app.osprey.actions.standardize

import app.osprey.actions.TakenNames
import app.osprey.actions.displayName
import app.osprey.actions.imageExtensions
import app.osprey.actions.resolveMediaDate
import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.videoExtensions
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.history.stateOf
import app.osprey.jobs.JobControl
import app.osprey.jobs.NoJobControl
import app.osprey.jobs.applyToFile
import app.osprey.jobs.forEachUntilStopped
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import java.time.format.DateTimeFormatter

private val STANDARDIZED_NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

// Between a standardized name and its number when two files get the same name: IMG_..._15-26-02__dup2.jpg.
private const val COLLISION_MARKER = "__dup"

/**
 * Renames every image/video directly inside `root` to `IMG_YYYY-MM-DD_HH-MM-SS` / `VID_...`,
 * resolving its own capture date independently of whether the Dates action (#6) has already run
 * — a POC invariant, not an accident of shared code (see [resolveMediaDate]). A straight
 * re-platform of the POC's `plan_standardize`/`apply_standardize`/`describe_standardize`.
 */
class StandardizePlanner(
    private val fs: FileSystemRepository,
    private val mediaMetadataReader: MediaMetadataReader,
) {

    /**
     * Scans `root`'s immediate files — subfolders are never touched, since they simply
     * aren't files. A non-image/video extension is skipped entirely
     * (not even considered for [StandardizePlan.unfixable]). An already-standardized name is a
     * silent no-op, never a false [StandardizePlan.unfixable] entry.
     */
    fun plan(root: FileHandle, control: JobControl = NoJobControl): StandardizePlan {
        val names = TakenNames(fs.list(root).map { it.name })
        val renames = mutableListOf<StandardizeRename>()
        val unfixable = mutableListOf<FileEntry>()

        control.forEachUntilStopped(fs.list(root).filterNot { it.isDirectory }, FileEntry::name) { entry ->
            when (val outcome = classify(entry, names)) {
                is EntryOutcome.Rename -> {
                    names.claim(outcome.rename.targetName)
                    renames += outcome.rename
                }
                EntryOutcome.Unfixable -> unfixable += entry
                else -> Unit // EntryOutcome.Skip: nothing to do
            }
        }
        return StandardizePlan(renames, unfixable)
    }

    private sealed interface EntryOutcome {
        data class Rename(val rename: StandardizeRename) : EntryOutcome
        data object Unfixable : EntryOutcome
        data object Skip : EntryOutcome
    }

    private fun classify(entry: FileEntry, names: TakenNames): EntryOutcome {
        val prefix = prefixFor(entry.name) ?: return EntryOutcome.Skip
        val resolved = resolveMediaDate(entry.handle, entry.name, mediaMetadataReader) ?: return EntryOutcome.Unfixable
        val targetName = targetNameFor(entry.name, prefix, resolved, names)
        if (targetName == entry.name) return EntryOutcome.Skip
        val rename = StandardizeRename(
            entry.handle,
            entry.name,
            targetName,
            resolved.dateTime,
            resolved.source,
            entry.sizeBytes,
        )
        return EntryOutcome.Rename(rename)
    }

    /** Logs an `UNFIXABLE` line per unresolved file, then a `RENAME` line per rename. Returns the rename count. */
    fun describe(plan: StandardizePlan, log: (String) -> Unit): Int {
        for (entry in plan.unfixable) {
            log("  UNFIXABLE ${entry.name}")
        }
        for (rename in plan.renames) {
            log("  RENAME ${rename.sourceName} -> ${rename.targetName}  (from ${displayName(rename.source)})")
        }
        return plan.renames.size
    }

    /**
     * Renames every file in [plan], stopping between two files when [control] says so; returns how many.
     * A file the file system refuses is recorded as failed and the next one is still renamed (#79).
     */
    fun apply(plan: StandardizePlan, control: JobControl = NoJobControl): Int {
        var renamed = 0
        control.forEachUntilStopped(plan.renames, StandardizeRename::sourceName) { rename ->
            val worked = control.applyToFile(rename.sourceName) {
                before = fs.stateOf(rename.entry, rename.sourceName, rename.sizeBytes)
                val target = fs.rename(rename.entry, rename.targetName)
                after = fs.stateOf(target, rename.targetName, rename.sizeBytes)
            }
            if (worked) renamed++
        }
        return renamed
    }

    private fun prefixFor(name: String): String? {
        val suffix = splitStemAndSuffix(name).second.lowercase()
        return when {
            suffix in imageExtensions -> "IMG"
            suffix in videoExtensions -> "VID"
            else -> null
        }
    }

    private fun targetNameFor(
        sourceName: String,
        prefix: String,
        resolved: MediaDate,
        names: TakenNames,
    ): String {
        val suffix = splitStemAndSuffix(sourceName).second
        val baseName = "${prefix}_${resolved.dateTime.format(STANDARDIZED_NAME_FORMAT)}"
        // The name this file should have if no other file does: a collision copy keeps its number.
        val number = collisionNumber(sourceName, baseName, suffix)
        val wanted = if (number == null) "$baseName$suffix" else "$baseName$COLLISION_MARKER$number$suffix"
        if (wanted == sourceName) return sourceName
        return firstFreeName(baseName, suffix, wanted, sourceName, names)
    }

    // A file Standardize already gave a collision number to stays as it is (#84): its own name counts as taken, so
    // every run would otherwise move it to the next free number (__dup2, __dup3, ...).
    private fun collisionNumber(sourceName: String, baseName: String, suffix: String): String? = Regex(
        "${Regex.escape(baseName)}$COLLISION_MARKER(\\d+)${Regex.escape(suffix)}",
        RegexOption.IGNORE_CASE,
    ).matchEntire(sourceName)?.let { it.groupValues[1] }

    // Starts at __dup2, not __dup1 like Dedupe's/Flatten's collision suffix -- carried over
    // from the POC as-is, a known inconsistency, not silently "fixed" during the port (#7).
    private fun firstFreeName(
        baseName: String,
        suffix: String,
        firstCandidate: String,
        sourceName: String,
        names: TakenNames,
    ): String {
        var target = firstCandidate
        var n = 2
        while (names.takenByAnother(target, sourceName)) {
            target = "$baseName$COLLISION_MARKER$n$suffix"
            n++
        }
        return target
    }
}
