package app.osprey.actions.reorg

import app.osprey.actions.resolveMediaDate
import app.osprey.filesystem.DirectoryNotEmptyException
import app.osprey.filesystem.FileAlreadyExistsException
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.media.MediaMetadataReader
import app.osprey.settings.InputMode
import java.io.IOException

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
     * or already planned from an earlier input, is listed and skipped.
     */
    fun plan(output: FileHandle, sources: List<ReorgSource>): ReorgPlan {
        val outputFiles = scanMedia(fs, output)
        val known = ContentIndex(fs).also { index -> outputFiles.forEach { index.add(it.entry) } }
        val names = DestinationNames(fs, output)
        val items = mutableListOf<ReorgItem>()
        val alreadyInOutput = mutableListOf<String>()
        val undated = mutableListOf<String>()

        for (file in outputFiles) {
            val year = yearOf(file.entry)
            when {
                year == null -> undated += file.relativePath
                file.folders != listOf(year.toString()) ->
                    items += item(file, file.relativePath, ReorgOperation.REFILE, year, names)
            }
        }
        for ((source, folder) in sources.mapNotNull { s -> s.folder?.let { s to it } }) {
            for (file in scanMedia(fs, folder)) {
                val label = "${source.label}/${file.relativePath}"
                val year = yearOf(file.entry)
                when {
                    known.holdsContentOf(file.entry) -> alreadyInOutput += label
                    year == null -> undated += label
                    else -> {
                        known.add(file.entry)
                        items += item(file, label, operationFor(source.mode), year, names)
                    }
                }
            }
        }
        val unreachable = sources.filter { it.folder == null }.map { it.label }
        return ReorgPlan(items, alreadyInOutput, undated, unreachable)
    }

    private fun yearOf(entry: FileEntry): Int? =
        resolveMediaDate(entry.handle, entry.name, mediaMetadataReader)?.dateTime?.year

    private fun operationFor(mode: InputMode) =
        if (mode == InputMode.MOVE) ReorgOperation.MOVE else ReorgOperation.COPY

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
        plan.alreadyInOutput.forEach { log("  SKIP $it  (content already in the output)") }
        plan.undated.forEach { log("  UNDATED $it") }
        plan.unreachable.forEach { log("  UNREACHABLE $it  (not granted or not found, skipped)") }
        return plan.items.size
    }

    /**
     * Performs exactly [plan]. A file that fails is left as it was and reported; the rest carry on.
     * [shouldStop] is asked before each file, so stopping never interrupts one in the middle.
     */
    fun apply(
        plan: ReorgPlan,
        output: FileHandle,
        shouldStop: () -> Boolean = { false },
        progress: (ReorgProgress) -> Unit = {},
    ): ReorgResult {
        val counts = mutableMapOf<ReorgOperation, Int>()
        val failures = mutableListOf<ReorgFailure>()
        val yearFolders = mutableMapOf<Int, FileHandle>()
        var cancelled = false
        for ((index, item) in plan.items.withIndex()) {
            cancelled = shouldStop()
            if (cancelled) break
            progress(ReorgProgress(index, plan.items.size, item.label))
            val failure = attempt(item) {
                perform(item, yearFolders.getOrPut(item.year) { fs.createFolder(output, item.year.toString()) })
            }
            if (failure == null) counts.merge(item.operation, 1, Int::plus) else failures += failure
        }
        return ReorgResult(
            copied = counts[ReorgOperation.COPY] ?: 0,
            moved = counts[ReorgOperation.MOVE] ?: 0,
            refiled = counts[ReorgOperation.REFILE] ?: 0,
            failures = failures,
            cancelled = cancelled,
        )
    }

    private fun perform(item: ReorgItem, dest: FileHandle) {
        when (item.operation) {
            ReorgOperation.REFILE -> fs.move(item.source, dest, item.destName)
            ReorgOperation.COPY -> fs.copyTo(item.source, dest, item.destName)
            ReorgOperation.MOVE -> moveVerified(item, dest)
        }
    }

    /** Copy, check size then content, and only then delete the source; a bad copy is discarded. */
    private fun moveVerified(item: ReorgItem, dest: FileHandle) {
        val copy = fs.copyTo(item.source, dest, item.destName)
        val copySize = fs.list(dest).firstOrNull { it.name == item.destName }?.sizeBytes
        val verified = copySize == item.sizeBytes && fs.contentHash(copy) == fs.contentHash(item.source)
        if (!verified) {
            fs.delete(copy)
            throw VerificationException("the copy differs from the source")
        }
        fs.delete(item.source)
    }

    private fun attempt(item: ReorgItem, action: () -> Unit): ReorgFailure? = try {
        action()
        null
    } catch (e: IOException) {
        ReorgFailure(item.label, e.message.orEmpty())
    } catch (e: FileAlreadyExistsException) {
        ReorgFailure(item.label, e.message.orEmpty())
    } catch (e: DirectoryNotEmptyException) {
        ReorgFailure(item.label, e.message.orEmpty())
    } catch (e: VerificationException) {
        ReorgFailure(item.label, e.message.orEmpty())
    } catch (e: IllegalStateException) {
        ReorgFailure(item.label, e.message.orEmpty())
    } catch (e: SecurityException) {
        ReorgFailure(item.label, e.message.orEmpty())
    }

    private class VerificationException(message: String) : Exception(message)
}
