package app.osprey.actions.dates

import app.osprey.filesystem.FileEntry

/**
 * The result of [DatesPlanner.plan]. Nothing on disk changes here — applying a resolved date to
 * the real filesystem is `:desktop`/`:android`'s job, via their real `FileSystemRepository`.
 */
data class DatesPlan(
    val fixes: List<DatesFix>,
    val unfixable: List<FileEntry>,
)
