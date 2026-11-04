package app.osprey.history

/** The files of one run that ended the same way: a run page lists failures first, so they are easy to find. */
data class RunFileGroup(val result: FileResult, val files: List<FileRecord>) {
    val title: String get() = "${result.name} (${files.size})"
}

private val GROUP_ORDER = listOf(FileResult.Failed, FileResult.Skipped, FileResult.Done)

/** The files of [run] grouped by how they ended, failures first; a result no file ended with has no group. */
fun runFileGroups(run: RunRecord): List<RunFileGroup> =
    GROUP_ORDER.map { result -> RunFileGroup(result, run.files.filter { it.result == result }) }
        .filter { it.files.isNotEmpty() }
