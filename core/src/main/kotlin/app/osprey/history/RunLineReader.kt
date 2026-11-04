package app.osprey.history

import java.time.Instant

private fun Map<*, *>.long(key: String): Long? = this[key] as? Long

private fun Map<*, *>.text(key: String): String? = this[key] as? String

private fun Map<*, *>.time(key: String): Instant? = long(key)?.let { Instant.ofEpochMilli(it) }

private inline fun <reified E : Enum<E>> Map<*, *>.named(key: String): E? {
    val name = text(key)
    return enumValues<E>().firstOrNull { it.name == name }
}

/**
 * Reads one line of a run file. A required field that is missing or of the wrong type makes the line unreadable; an
 * optional one is simply absent. Fields this version does not know are ignored.
 */
internal object RunLineReader {
    fun read(text: String): RunLine? = parse(text)?.let { fields ->
        when (fields.text("t")) {
            "run" -> header(fields)
            "start" -> fields.time("at")?.let(RunLine::Started)
            "log" -> fields.text("line")?.let(RunLine::Logged)
            "file" -> file(fields)
            "end" -> ended(fields)
            else -> null
        }
    }

    @Suppress("SwallowedException") // A cut or damaged line is an expected answer, not an error: it reads as null.
    private fun parse(text: String): Map<*, *>? = try {
        Json.read(text) as? Map<*, *>
    } catch (ignored: JsonException) {
        null
    }

    private fun header(fields: Map<*, *>): RunLine? {
        val id = fields.long("id")
        val action = fields.text("action")
        val at = fields.time("at")
        return if (id != null && action != null && at != null) RunLine.Header(id, action, at) else null
    }

    private fun file(fields: Map<*, *>): RunLine? {
        val path = fields.text("path")
        val result = fields.named<FileResult>("result")
        if (path == null || result == null) return null
        val message = fields.text("message").orEmpty()
        return RunLine.Filed(FileRecord(path, result, message, state(fields["before"]), state(fields["after"])))
    }

    private fun state(value: Any?): FileState? {
        val fields = value as? Map<*, *>
        val size = fields?.long("size")
        if (fields == null || size == null) return null
        return FileState(
            size, fields.time("created"), fields.time("modified"), fields.text("hash"), fields.text("name"),
        )
    }

    private fun ended(fields: Map<*, *>): RunLine? {
        val state = fields.named<RunState>("state")
        val at = fields.time("at")
        return if (state != null && at != null) RunLine.Ended(state, fields.text("summary").orEmpty(), at) else null
    }
}
