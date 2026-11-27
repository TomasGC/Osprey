package app.osprey.desktop

import app.osprey.desktop.testing.TimedTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWorkersTest : TimedTest() {

    @Test
    fun `a scan decodes one file per core, four at most and one at least`() {
        assertEquals(listOf(1, 1, 2, 3, 4, 4, 4), listOf(0, 1, 2, 3, 4, 8, 22).map { scanWorkers(it) })
    }

    @Test
    fun `without a number the machine's cores are used`() {
        assertEquals(scanWorkers(Runtime.getRuntime().availableProcessors()), scanWorkers())
    }
}
