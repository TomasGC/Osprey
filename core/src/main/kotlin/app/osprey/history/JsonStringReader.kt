package app.osprey.history

private const val HEX_DIGITS = 4
private const val HEX_RADIX = 16

private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

/** Reads the strings of a JSON text, escapes included. */
internal class JsonStringReader(private val cursor: JsonCursor) {
    fun read(): String {
        cursor.expect('"')
        val out = StringBuilder()
        while (true) {
            if (cursor.atEnd) cursor.fail("unterminated string")
            val c = cursor.text[cursor.at++]
            when {
                c == '"' -> return out.toString()
                c == '\\' -> out.append(readEscape())
                c < ' ' -> cursor.fail("control character in string")
                else -> out.append(c)
            }
        }
    }

    private fun readEscape(): Char {
        if (cursor.atEnd) cursor.fail("unterminated string")
        return when (val e = cursor.text[cursor.at++]) {
            '"', '\\', '/' -> e
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            'b' -> '\b'
            'f' -> '\u000C'
            'u' -> readHex4()
            else -> cursor.fail("bad escape '\\$e'")
        }
    }

    private fun readHex4(): Char {
        val digits = cursor.text.substring(cursor.at, minOf(cursor.at + HEX_DIGITS, cursor.text.length))
        if (digits.length < HEX_DIGITS || !digits.all { isHex(it) }) cursor.fail("bad \\u escape")
        cursor.at += HEX_DIGITS
        return digits.toInt(HEX_RADIX).toChar()
    }
}
