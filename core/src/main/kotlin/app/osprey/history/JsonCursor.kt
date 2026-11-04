package app.osprey.history

/** Where a JSON reader is in its text, and the one way it gives up. */
internal class JsonCursor(val text: String) {
    var at = 0

    val atEnd: Boolean get() = at == text.length

    fun fail(why: String): Nothing = throw JsonException("$why at $at")

    fun skipSpaces() {
        while (!atEnd && text[at] in " \t\r\n") at++
    }

    /** The next non-space character, without moving past it. */
    fun peek(): Char {
        skipSpaces()
        if (atEnd) fail("unexpected end")
        return text[at]
    }

    fun expect(c: Char) {
        if (peek() != c) fail("expected '$c'")
        at++
    }
}
