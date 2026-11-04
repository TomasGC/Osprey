package app.osprey.history

/** Writes and reads the lines of a run file (#79). Times are epoch milliseconds. */
object RunLines {
    /** The one-line JSON text of [line]. */
    fun encode(line: RunLine): String = Json.write(
        when (line) {
            is RunLine.Header ->
                linkedMapOf("t" to "run", "id" to line.id, "action" to line.action, "at" to line.at.toEpochMilli())
            is RunLine.Started -> linkedMapOf("t" to "start", "at" to line.at.toEpochMilli())
            is RunLine.Logged -> linkedMapOf("t" to "log", "line" to line.line)
            is RunLine.Filed -> fileFields(line.record)
            is RunLine.Ended -> linkedMapOf(
                "t" to "end", "state" to line.state.name, "summary" to line.summary, "at" to line.at.toEpochMilli(),
            )
        },
    )

    private fun fileFields(record: FileRecord): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>(
            "t" to "file", "path" to record.path, "result" to record.result.name, "message" to record.message,
        )
        record.before?.let { fields["before"] = stateFields(it) }
        record.after?.let { fields["after"] = stateFields(it) }
        return fields
    }

    private fun stateFields(state: FileState): Map<String, Any?> {
        val fields = linkedMapOf<String, Any?>("size" to state.size)
        state.created?.let { fields["created"] = it.toEpochMilli() }
        state.modified?.let { fields["modified"] = it.toEpochMilli() }
        state.hash?.let { fields["hash"] = it }
        state.name?.let { fields["name"] = it }
        return fields
    }

    /** The line in [text], or null if it is not a line this version can read (cut, damaged, unknown). */
    fun decode(text: String): RunLine? = RunLineReader.read(text)

    /** The run the lines of one file describe, or null when none of them is a readable header. */
    fun replay(lines: List<String>): RunRecord? {
        var run: RunRecord? = null
        for (text in lines) {
            val line = decode(text) ?: continue
            val current = run
            run = when {
                current != null -> apply(current, line)
                line is RunLine.Header -> RunRecord(line.id, line.action, RunState.Queued, queuedAt = line.at)
                else -> null
            }
        }
        return run
    }

    private fun apply(run: RunRecord, line: RunLine): RunRecord = when (line) {
        is RunLine.Header -> run
        is RunLine.Started -> run.copy(state = RunState.Running, startedAt = line.at)
        is RunLine.Logged -> run.copy(log = run.log + line.line)
        is RunLine.Filed -> run.copy(files = run.files + line.record)
        is RunLine.Ended -> run.copy(state = line.state, summary = line.summary, endedAt = line.at)
    }
}
