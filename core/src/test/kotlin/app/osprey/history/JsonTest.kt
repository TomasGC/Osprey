package app.osprey.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {

    private fun assertRejected(text: String) {
        try {
            Json.read(text)
            fail("accepted: $text")
        } catch (expected: JsonException) {
            // the right answer
        }
    }

    @Test
    fun `it writes the scalars`() {
        assertEquals("null", Json.write(null))
        assertEquals("true", Json.write(true))
        assertEquals("false", Json.write(false))
        assertEquals("1700000000000", Json.write(1_700_000_000_000L))
        assertEquals("-7", Json.write(-7))
        assertEquals("\"abc\"", Json.write("abc"))
    }

    @Test
    fun `it writes lists and objects on one line in order`() {
        assertEquals("[1,\"a\",null]", Json.write(listOf(1L, "a", null)))
        assertEquals("[]", Json.write(emptyList<Any?>()))
        assertEquals("{\"b\":1,\"a\":[true]}", Json.write(linkedMapOf("b" to 1L, "a" to listOf(true))))
        assertEquals("{}", Json.write(emptyMap<String, Any?>()))
    }

    @Test
    fun `it escapes what a line cannot hold`() {
        val text = "a\"b\\c\nd\te\rf\u0001g\bh\u000Ci"

        assertEquals("\"" + """a\"b\\c\nd\te\rf\u0001g\bh\fi""" + "\"", Json.write(text))
    }

    @Test
    fun `it writes other characters as they are`() {
        assertEquals("\"/\u00e9\uD83D\uDE00\"", Json.write("/\u00e9\uD83D\uDE00"))
    }

    @Test
    fun `it refuses a type the files never hold`() {
        assertThrows(IllegalArgumentException::class.java) { Json.write(1.5) }
        assertThrows(IllegalArgumentException::class.java) { Json.write(mapOf(1 to 2)) }
    }

    @Test
    fun `it reads the scalars`() {
        assertEquals(null, Json.read("null"))
        assertEquals(true, Json.read("true"))
        assertEquals(false, Json.read("false"))
        assertEquals(0L, Json.read("0"))
        assertEquals(-42L, Json.read("-42"))
        assertEquals(Long.MIN_VALUE, Json.read("-9223372036854775808"))
        assertEquals("abc", Json.read("\"abc\""))
    }

    @Test
    fun `it reads lists and objects, spaces allowed anywhere between tokens`() {
        assertEquals(listOf(1L, "a", null), Json.read(" [ 1 , \"a\" , null ] "))
        assertEquals(emptyList<Any?>(), Json.read("[ ]"))
        assertEquals(mapOf("a" to listOf(true), "b" to 1L), Json.read("\t{ \"a\" : [ true ] ,\n\"b\":1 }\r\n"))
        assertEquals(emptyMap<String, Any?>(), Json.read("{ }"))
    }

    @Test
    fun `it reads every escape`() {
        assertEquals("a\"b\\c/d\ne\tf\rg\bh\u000Ci", Json.read("""  "a\"b\\c\/d\ne\tf\rg\bh\fi"  """))
        assertEquals("\u00e9", Json.read("\"\\u00e9\""))
        assertEquals("\u00E9", Json.read("\"\\u00E9\""))
        assertEquals("\uD83D\uDE00", Json.read("\"\\ud83d\\ude00\""))
    }

    @Test
    fun `what it writes it reads back`() {
        val value = mapOf(
            "text" to "q\"\\\n\t\u0001 \u00e9\uD83D\uDE00",
            "list" to listOf(1L, null, "x\n", listOf(false), emptyMap<String, Any?>()),
            "nested" to mapOf("c" to true, "n" to -3L),
        )

        assertEquals(value, Json.read(Json.write(value)))
    }

    @Test
    fun `it rejects text that is not exactly one value`() {
        listOf(
            "", "   ", "[", "{", "[1,", "[1,]", "[1 2]", "[1}", "{\"a\"", "{\"a\":", "{\"a\":1", "{\"a\":1,}",
            "{\"a\" 1}", "{1:2}", "{\"a\":1 \"b\":2}", "\"abc", "\"a\\x\"", "\"\\", "\"\\u12\"", "\"\\u12zz\"",
            "\"a\nb\"", "tru", "nul", "nulls", "fals", "x", "1.5", "1e5", "-", "-x", "1 2", "{\"a\":1}x", "[]]",
            "\"\\u12", "\"\\u+123\"", "\"\\u-123\"",
            "[1 2 3]", "{\"a\":1 x \"b\":2}", "+1",
        ).forEach(::assertRejected)
    }
}
