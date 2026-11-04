package app.osprey.history

/** Raised when a text is not JSON the history files can contain. */
class JsonException(message: String) : IllegalArgumentException(message)

/**
 * The small JSON the run files use (#79), with no library: null, booleans, whole numbers, strings, lists and
 * objects, held as null, Boolean, Long, String, List and Map<String, Any?>. Fractions are not part of it.
 */
object Json {
    /** One line of JSON for [value]. Throws [IllegalArgumentException] for a type the files never hold. */
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    /** The value in [text]. Throws [JsonException] if [text] is not exactly one JSON value. */
    fun read(text: String): Any? = Reader(text).readDocument()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean, is Int, is Long -> out.append(value)
            is String -> appendString(out, value)
            is List<*> -> appendAll(out, '[', ']', value) { append(out, it) }
            is Map<*, *> -> appendAll(out, '{', '}', value.entries) { entry ->
                val key = requireNotNull(entry.key as? String) { "JSON keys are strings: ${entry.key}" }
                appendString(out, key)
                out.append(':')
                append(out, entry.value)
            }
            else -> throw IllegalArgumentException("Not a JSON value: ${value::class.simpleName}")
        }
    }

    private fun <T> appendAll(out: StringBuilder, open: Char, close: Char, items: Iterable<T>, each: (T) -> Unit) {
        out.append(open)
        items.forEachIndexed { index, item ->
            if (index > 0) out.append(',')
            each(item)
        }
        out.append(close)
    }

    private fun appendString(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\t' -> out.append("\\t")
                '\r' -> out.append("\\r")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Reader(text: String) {
        private val cursor = JsonCursor(text)
        private val strings = JsonStringReader(cursor)

        fun readDocument(): Any? {
            val value = readValue()
            cursor.skipSpaces()
            if (!cursor.atEnd) cursor.fail("unexpected '${cursor.text[cursor.at]}'")
            return value
        }

        private fun readValue(): Any? = when (val c = cursor.peek()) {
            '{' -> readObject()
            '[' -> readList()
            '"' -> strings.read()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c in '0'..'9') readNumber() else cursor.fail("unexpected '$c'")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!cursor.text.startsWith(word, cursor.at)) cursor.fail("expected $word")
            cursor.at += word.length
            return value
        }

        private fun readNumber(): Long {
            val start = cursor.at
            if (cursor.text[cursor.at] == '-') cursor.at++
            while (!cursor.atEnd && cursor.text[cursor.at] in '0'..'9') cursor.at++
            return cursor.text.substring(start, cursor.at).toLongOrNull() ?: cursor.fail("bad number")
        }

        private fun readList(): List<Any?> = readSeries('[', ']') { readValue() }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            readSeries('{', '}') {
                val key = strings.read()
                cursor.expect(':')
                map[key] = readValue()
            }
            return map
        }

        /** Items separated by commas between [open] and [close]; each call of [item] reads one. */
        private fun <T> readSeries(open: Char, close: Char, item: () -> T): List<T> {
            cursor.expect(open)
            val items = ArrayList<T>()
            if (cursor.peek() == close) {
                cursor.at++
                return items
            }
            while (true) {
                items += item()
                when (cursor.peek()) {
                    ',' -> cursor.at++
                    close -> {
                        cursor.at++
                        return items
                    }
                    else -> cursor.fail("expected ',' or '$close'")
                }
            }
        }
    }
}
