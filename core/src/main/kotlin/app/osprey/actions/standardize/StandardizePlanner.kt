package app.osprey.actions.standardize

import app.osprey.actions.displayName
import app.osprey.actions.imageExtensions
import app.osprey.actions.resolveMediaDate
import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.videoExtensions
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.media.MediaDate
import app.osprey.media.MediaMetadataReader
import java.time.format.DateTimeFormatter

private val STANDARDIZED_NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

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
     * Scans `root`'s immediate files — `TODELETE`/`TODELETEDUPS` and other subfolders are never
     * touched, since they simply aren't files. A non-image/video extension is skipped entirely
     * (not even considered for [StandardizePlan.unfixable]). An already-standardized name is a
     * silent no-op, never a false [StandardizePlan.unfixable] entry.
     */
    fun plan(root: FileHandle): StandardizePlan {
        val existingNames = fs.list(root).map { it.name }.toSet()
        val claimed = mutableSetOf<String>()
        val renames = mutableListOf<StandardizeRename>()
        val unfixable = mutableListOf<FileEntry>()

        for (entry in fs.list(root).filterNot { it.isDirectory }) {
            when (val outcome = classify(entry, existingNames, claimed)) {
                is EntryOutcome.Rename -> {
                    claimed += outcome.rename.targetName
                    renames += outcome.rename
                }
                EntryOutcome.Unfixable -> unfixable += entry
                EntryOutcome.Skip -> Unit
            }
        }
        return StandardizePlan(renames, unfixable)
    }

    private sealed interface EntryOutcome {
        data class Rename(val rename: StandardizeRename) : EntryOutcome
        data object Unfixable : EntryOutcome
        data object Skip : EntryOutcome
    }

    private fun classify(entry: FileEntry, existingNames: Set<String>, claimed: Set<String>): EntryOutcome {
        val prefix = prefixFor(entry.name) ?: return EntryOutcome.Skip
        val resolved = resolveMediaDate(entry.handle, entry.name, mediaMetadataReader) ?: return EntryOutcome.Unfixable
        val targetName = targetNameFor(entry.name, prefix, resolved, existingNames, claimed)
        if (targetName == entry.name) return EntryOutcome.Skip
        val rename = StandardizeRename(entry.handle, entry.name, targetName, resolved.dateTime, resolved.source)
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

    /** Renames every file in [plan]. */
    fun apply(plan: StandardizePlan) {
        for (rename in plan.renames) {
            fs.rename(rename.entry, rename.targetName)
        }
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
        existingNames: Set<String>,
        claimed: Set<String>,
    ): String {
        val suffix = splitStemAndSuffix(sourceName).second
        val baseName = "${prefix}_${resolved.dateTime.format(STANDARDIZED_NAME_FORMAT)}"
        val target = "$baseName$suffix"
        if (target == sourceName) return target
        return firstFreeName(baseName, suffix, target, existingNames, claimed)
    }

    // Starts at __dup2, not __dup1 like Dedupe's/Flatten's collision suffix -- carried over
    // from the POC as-is, a known inconsistency, not silently "fixed" during the port (#7).
    private fun firstFreeName(
        baseName: String,
        suffix: String,
        firstCandidate: String,
        existingNames: Set<String>,
        claimed: Set<String>,
    ): String {
        var target = firstCandidate
        var n = 2
        while (target in existingNames || target in claimed) {
            target = "${baseName}__dup$n$suffix"
            n++
        }
        return target
    }
}
