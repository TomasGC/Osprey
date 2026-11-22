package app.osprey.actions.generated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedMixesTest {

    private fun failureOf(seeds: List<Long>, property: (List<FileSpec>) -> Unit): AssertionError {
        val thrown = runCatching { GeneratedMixes.check(seeds, property) }.exceptionOrNull()
        assertTrue("the check passed", thrown is AssertionError)
        return thrown as AssertionError
    }

    @Test
    fun `a property that always holds passes`() {
        GeneratedMixes.check { mix -> assertTrue(mix.isNotEmpty()) }
    }

    @Test
    fun `runs one round per seed, in order, from the first seed`() {
        val seen = mutableListOf<List<FileSpec>>()

        GeneratedMixes.check { seen += it }

        assertEquals(GeneratedMixes.ROUNDS, seen.size)
        assertEquals(MixGenerator.mix(GeneratedMixes.FIRST_SEED), seen.first())
        assertEquals(MixGenerator.mix(GeneratedMixes.FIRST_SEED + GeneratedMixes.ROUNDS - 1), seen.last())
    }

    @Test
    fun `a failure names its seed, its message and the smallest mix`() {
        val error = failureOf(listOf(7L)) { mix -> assertEquals("one file only", 1, mix.size) }

        val message = error.message.orEmpty()
        val full = MixGenerator.mix(7L)
        assertTrue(message, message.startsWith("seed=7: one file only"))
        assertTrue(message, "smallest mix (2 of ${full.size} files)" in message)
        assertTrue(message, "GeneratedMixes.check(listOf(7L))" in message)
        assertEquals(2, message.lines().count { it.startsWith("  #") })
    }

    @Test
    fun `an exception of the property is reported with its seed too`() {
        val error = failureOf(listOf(11L)) { error("boom") }

        val message = error.message.orEmpty()
        assertTrue(message, message.startsWith("seed=11: IllegalStateException: boom"))
        assertTrue(message, "smallest mix (1 of" in message)
    }

    @Test
    fun `a failure without a message still says that it failed`() {
        val error = failureOf(listOf(3L)) { throw AssertionError() }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().startsWith("seed=3: assertion failed"))
    }

    @Test
    fun `stops at the first failing seed and runs only the seeds it is given`() {
        val seen = mutableListOf<List<FileSpec>>()

        val error = failureOf(listOf(5L, 6L, 7L)) { mix ->
            seen += mix
            throw AssertionError("always")
        }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().startsWith("seed=5: always"))
        assertTrue("seed 5 ran", MixGenerator.mix(5L) in seen)
        assertFalse("seed 6 ran", MixGenerator.mix(6L) in seen)
        assertFalse("seed 7 ran", MixGenerator.mix(7L) in seen)
    }
}
