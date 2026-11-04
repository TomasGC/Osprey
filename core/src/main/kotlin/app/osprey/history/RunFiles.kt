package app.osprey.history

import java.io.File
import java.io.IOException

private const val EXTENSION = "jsonl"

/** The files of the runs in one folder (#79): reads them at start, adds a line to one, deletes the ones that go. */
internal class RunFiles(private val dir: File) {
    private class Loaded(val run: RunRecord, val file: File, val terminated: Boolean)

    private val files = mutableMapOf<Long, File>()

    /** Ids whose file ended in a cut line: the next line written to it starts on a new line. */
    private val cut = mutableSetOf<Long>()

    /** Every run the folder holds. A file that cannot be read is skipped and left alone. */
    fun load(): List<RunRecord> {
        val loaded = dir.listFiles { file -> file.extension == EXTENSION }.orEmpty()
            .sortedBy { it.name }
            .mapNotNull { read(it) }
            .distinctBy { it.run.id }
        for (item in loaded) {
            files[item.run.id] = item.file
            if (!item.terminated) cut += item.run.id
        }
        return loaded.map { it.run }
    }

    private fun read(file: File): Loaded? = try {
        val text = file.readText()
        RunLines.replay(text.lines())?.let { Loaded(it, file, text.endsWith("\n")) }
    } catch (ignored: IOException) {
        null
    }

    /** Adds [line] to the file of run [id], creating the folder and the file on the first one. */
    fun append(id: Long, line: RunLine) {
        val file = files.getOrPut(id) { File(dir, "$id.$EXTENSION") }
        val glue = if (id in cut) "\n" else ""
        try {
            dir.mkdirs()
            file.appendText(glue + RunLines.encode(line) + "\n")
            cut -= id
        } catch (ignored: IOException) {
            // Losing this line must not stop the run: the screens read the runs from memory.
        }
    }

    /** Deletes the file of every run not in [keep]; a file this class never read or wrote is not touched. */
    fun keepOnly(keep: Set<Long>) {
        for (id in files.keys - keep) {
            files.getValue(id).delete()
            files -= id
            cut -= id
        }
    }
}
