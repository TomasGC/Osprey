package app.osprey.actions.reorg

import app.osprey.actions.resolveMediaDate
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.failureReason
import app.osprey.jobs.JobControl
import app.osprey.jobs.NoJobControl
import app.osprey.jobs.forEachUntilStopped
import app.osprey.jobs.recordResult
import app.osprey.media.MediaMetadataReader
import app.osprey.settings.InputMode
import java.time.LocalDateTime

/**
 * Gathers the images and videos of the input folders, and the misfiled ones of the output, into
 * `<output>/<year>/`. The year comes from [resolveMediaDate], the resolution every action shares.
 * Move is copy, verify, delete: a source is deleted only once the copy has the same size and the
 * same content, so an interruption can leave a file twice but never zero times.
 */
class ReorgPlanner(
    private val fs: FileSystemRepository,
    private val mediaMetadataReader: MediaMetadataReader,
) {

    /**
     * Reads [output] and every reachable input; nothing on disk changes. An input with no folder
     * is listed as unreachable and skipped. An input file whose content is already in the output,
     * or already planned from an earlier input, is listed and skipped. [control] is told which file is
     * next and asked between two files whether to stop; a stopped scan returns what it had so far.
     */
    fun plan(
        output: FileHandle,
        sources: List<ReorgSource>,
        control: JobControl = NoJobControl,
    ): ReorgPlan {
        val outputFiles = scanMedia(fs, output)
        val reachable = sources.mapNotNull { s -> s.folder?.let { s to it } }
        val visits = outputFiles.map { Visit(it, null) } +
            reachable.flatMap { (source, folder) -> scanMedia(fs, folder).map { Visit(it, source) } }
        val known = ContentIndex(fs).also { index -> outputFiles.forEach { index.add(it.entry) } }
        val library = outputFiles.associateBy { it.entry.handle }
        val names = DestinationNames(fs, output)
        val items = mutableListOf<ReorgItem>()
        val alreadyInOutput = mutableListOf<String>()
        val duplicates = mutableListOf<ReorgDuplicate>()
        val undated = mutableListOf<String>()

        control.forEachUntilStopped(visits, Visit::label) { visit ->
            val year = yearOf(visit.file.entry)
            val source = visit.source
            val twin = if (source == null) null else known.firstMatch(visit.file.entry)
            val moveTwin = source?.takeIf { it.mode == InputMode.MOVE }
                ?.let { move -> twin?.let { library[it] }?.let { move to it } }
            when {
                moveTwin != null ->
                    duplicates += ReorgDuplicate(
                        source = moveTwin.first.label,
                        input = visit.file.asSide(visit.label, year?.toString().orEmpty(), dateOf(visit.file.entry)),
                        library = moveTwin.second.asSide(
                            moveTwin.second.relativePath,
                            moveTwin.second.folders.firstOrNull().orEmpty(),
                            dateOf(moveTwin.second.entry),
                        ),
                    )
                twin != null -> alreadyInOutput += visit.label
                year == null -> undated += visit.label
                source != null -> {
                    known.add(visit.file.entry)
                    val operation = if (source.mode == InputMode.MOVE) ReorgOperation.MOVE else ReorgOperation.COPY
                    items += item(visit.file, visit.label, operation, year, names)
                }
                visit.file.folders != listOf(year.toString()) ->
                    items += item(visit.file, visit.label, ReorgOperation.REFILE, year, names)
            }
        }
        val unreachable = sources.filter { it.folder == null }.map { it.label }
        return ReorgPlan(items, alreadyInOutput, duplicates, undated, unreachable)
    }

    private fun yearOf(entry: FileEntry): Int? = dateOf(entry)?.year

    private fun dateOf(entry: FileEntry): LocalDateTime? =
        resolveMediaDate(entry.handle, entry.name, mediaMetadataReader)?.dateTime

    private fun item(
        file: ScannedFile,
        label: String,
        operation: ReorgOperation,
        year: Int,
        names: DestinationNames,
    ): ReorgItem {
        val destName = names.reserve(year, file.entry.name)
        val note = if (destName != file.entry.name) "(renamed, name collision)" else ""
        return ReorgItem(file.entry.handle, label, file.entry.sizeBytes, operation, year, destName, note)
    }

    /**
     * Logs one line per item (`COPY`, `MOVE` or `REFILE`), then the files skipped, left undated and
     * the inputs that could not be read. Returns the item count.
     */
    fun describe(plan: ReorgPlan, log: (String) -> Unit): Int {
        for (item in plan.items) {
            val suffix = if (item.note.isNotEmpty()) "  ${item.note}" else ""
            log("  ${item.operation} ${item.label} -> ${item.year}/${item.destName}$suffix")
        }
        plan.duplicates.forEach {
            log("  DUPLICATE ${it.input.label} == ${it.library.label}  (already in the library, you choose)")
        }
        plan.alreadyInOutput.forEach { log("  SKIP $it  (content already in the output)") }
        plan.undated.forEach { log("  UNDATED $it") }
        plan.unreachable.forEach { log("  UNREACHABLE $it  (not granted or not found, skipped)") }
        return plan.items.size
    }

    /**
     * Performs exactly [plan]. A file that fails is left as it was and reported; the rest carry on.
     * [control] is asked before each file, so stopping never interrupts one in the middle.
     */
    fun apply(plan: ReorgPlan, output: FileHandle, control: JobControl = NoJobControl): ReorgResult {
        val counts = mutableMapOf<ReorgOperation, Int>()
        val failures = mutableListOf<ReorgFailure>()
        val yearFolders = mutableMapOf<Int, FileHandle>()
        val finished = control.forEachUntilStopped(plan.items, ReorgItem::label) { item ->
            val failure = attempt(item) {
                perform(item, yearFolders.getOrPut(item.year) { fs.createFolder(output, item.year.toString()) })
            }
            control.recordResult(item.label, failure?.reason, item.doneMessage())
            if (failure == null) counts.merge(item.operation, 1, Int::plus) else failures += failure
        }
        return ReorgResult(
            copied = counts[ReorgOperation.COPY] ?: 0,
            moved = counts[ReorgOperation.MOVE] ?: 0,
            refiled = counts[ReorgOperation.REFILE] ?: 0,
            trashed = 0,
            failures = failures,
            cancelled = !finished,
        )
    }

    /**
     * [apply] for [plan], then the Trash side of [choices]: every unticked file of a duplicate pair (#91). A planned
     * refile of a library file that goes to the Trash is dropped, so it can never fail on a file that moved away.
     */
    fun apply(
        plan: ReorgPlan,
        output: FileHandle,
        choices: List<DuplicateChoice>,
        control: JobControl,
    ): ReorgResult {
        val trashedLibrary = choices.filterNot { it.keepLibrary }.map { it.duplicate.library.handle }.toSet()
        val (dropped, kept) = plan.items.partition {
            it.operation == ReorgOperation.REFILE && it.source in trashedLibrary
        }
        val base = apply(plan.copy(items = kept), output, control)
        if (base.cancelled) return base
        val outcome = DuplicateTrasher(fs, output).trash(choices, dropped.map { it.source }.toSet(), control)
        return base.copy(
            trashed = outcome.trashed,
            failures = base.failures + outcome.failures,
            cancelled = !outcome.finished,
        )
    }

    private fun ReorgItem.doneMessage(): String {
        val verb = when (operation) {
            ReorgOperation.COPY -> "copied"
            ReorgOperation.MOVE -> "moved"
            ReorgOperation.REFILE -> "refiled"
        }
        return "$verb to $year/$destName"
    }

    private fun perform(item: ReorgItem, dest: FileHandle) {
        when (item.operation) {
            ReorgOperation.REFILE -> fs.move(item.source, dest, item.destName)
            ReorgOperation.COPY -> fs.copyTo(item.source, dest, item.destName)
            ReorgOperation.MOVE -> fs.moveVerified(item.source, item.sizeBytes, dest, item.destName)
        }
    }

    private fun attempt(item: ReorgItem, action: () -> Unit): ReorgFailure? =
        failureReason(action)?.let { ReorgFailure(item.label, it) }

    /** A file found while planning: [source] is the input it came from, or null for a file of the output. */
    private class Visit(val file: ScannedFile, val source: ReorgSource?) {
        val label: String get() = if (source == null) file.relativePath else "${source.label}/${file.relativePath}"
    }
}
