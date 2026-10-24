package app.osprey.actions.trash

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashConfirmationTest {

    private val lib = TrashTestLibrary()

    @Test
    fun `one action of one year names that folder`() {
        val entries = listOf(
            lib.trash("2019", TrashAction.DEDUPE, "a.jpg", ByteArray(2048)),
            lib.trash("2019", TrashAction.DEDUPE, "b.jpg", ByteArray(2048)),
        )

        assertEquals(
            "Permanently delete 2 files (4 KB) from Trash/2019/Dedupe? This cannot be undone.",
            clearConfirmation(entries),
        )
    }

    @Test
    fun `several actions of one year name the year`() {
        val entries = listOf(
            lib.trash("2019", TrashAction.DEDUPE, "a.jpg", ByteArray(1024)),
            lib.trash("2019", TrashAction.VISUAL_DUPES, "b.jpg", ByteArray(1024)),
        )

        assertEquals(
            "Permanently delete 2 files (2 KB) from Trash/2019? This cannot be undone.",
            clearConfirmation(entries),
        )
    }

    @Test
    fun `several years say how many`() {
        val entries = listOf(
            lib.trash("2019", TrashAction.DEDUPE, "a.jpg", ByteArray(1024)),
            lib.trash("2020", TrashAction.DEDUPE, "b.jpg", ByteArray(1024)),
        )

        assertEquals(
            "Permanently delete 2 files (2 KB) from the Trash, across 2 years? This cannot be undone.",
            clearConfirmation(entries),
        )
    }

    @Test
    fun `a single file is singular`() {
        val entries = listOf(lib.trash("2019", TrashAction.DEDUPE, "a.jpg", ByteArray(10)))

        assertEquals(
            "Permanently delete 1 file (10 B) from Trash/2019/Dedupe? This cannot be undone.",
            clearConfirmation(entries),
        )
    }

    @Test
    fun `nothing selected still names the Trash and says it is final`() {
        assertEquals(
            "Permanently delete 0 files (0 B) from the Trash? This cannot be undone.",
            clearConfirmation(emptyList()),
        )
    }
}
