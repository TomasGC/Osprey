package app.osprey.actions.rename

import app.osprey.actions.splitStemAndSuffix
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository

/**
 * Strips sync/OS duplicate-copy suffixes ("photo (2).jpg", "photo - Copy.jpg", "photo_1.jpg")
 * from every file directly inside `root`. A straight re-platform of the POC's
 * `plan_rename`/`apply_rename`/`describe_rename` against [FileSystemRepository] instead of real
 * paths — the suffix-stripping rules themselves ([cleanStem]) need no repository at all.
 */
class RenamePlanner(private val fs: FileSystemRepository) {

    /**
     * Scans `root`: every file whose [cleanStem]-ed name differs from its current one is queued
     * to rename. A file whose target name already exists, or was already claimed earlier in
     * this same plan, is skipped rather than silently overwritten or crashing. Directories are
     * never considered. Nothing on disk changes — call [apply] to act on the result.
     */
    fun plan(root: FileHandle): RenamePlan {
        val entries = fs.list(root).sortedBy { it.name }
        val existingNames = entries.map { it.name }.toSet()
        val claimed = mutableSetOf<String>()
        val renames = mutableListOf<RenameAction>()
        val skips = mutableListOf<RenameAction>()

        for (entry in entries) {
            val action = entry.takeIf { !it.isDirectory }?.let { candidateRename(it.handle, it.name) } ?: continue
            if (action.targetName in existingNames || action.targetName in claimed) {
                skips += action
            } else {
                claimed += action.targetName
                renames += action
            }
        }
        return RenamePlan(renames, skips)
    }

    private fun candidateRename(handle: FileHandle, name: String): RenameAction? {
        val (stem, suffix) = splitStemAndSuffix(name)
        val newStem = cleanStem(stem)
        if (newStem == stem) return null
        return RenameAction(handle, name, "$newStem$suffix")
    }

    /** Logs skips, then renames — one line each. Returns the rename count. */
    fun describe(plan: RenamePlan, log: (String) -> Unit): Int {
        for (skip in plan.skips) {
            log("  SKIP (conflict) ${skip.sourceName} -> ${skip.targetName}")
        }
        for (rename in plan.renames) {
            log("  RENAME ${rename.sourceName} -> ${rename.targetName}")
        }
        return plan.renames.size
    }

    /** Renames every file in [plan]. */
    fun apply(plan: RenamePlan) {
        for (rename in plan.renames) {
            fs.rename(rename.source, rename.targetName)
        }
    }
}
