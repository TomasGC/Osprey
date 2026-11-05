package app.osprey.desktop

import app.osprey.desktop.testing.TimedTest
import app.osprey.desktop.testing.untimedTestClasses
import org.junit.Assert.assertEquals
import org.junit.Test

class TimedTestGuardTest : TimedTest() {

    @Test
    fun `every test class of the tier has the time limit`() {
        assertEquals("test classes that do not extend TimedTest", emptyList<String>(), untimedTestClasses(javaClass))
    }
}
