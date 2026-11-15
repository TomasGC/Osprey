package app.osprey.actions.reorg

import app.osprey.filesystem.FakeFileSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateReviewTest {

    private val fs = FakeFileSystemRepository()
    private val review = DuplicateReview()

    private fun pair(name: String, source: String = "Camera", year: String = "2018"): ReorgDuplicate {
        val input = DuplicateSide(fs.addFile(fs.root, "in-$source-$name"), "$source/$name", name, 4, null, year)
        val library = DuplicateSide(fs.addFile(fs.root, "lib-$year-$name"), "$year/$name", name, 4, null, year)
        return ReorgDuplicate(source, input, library)
    }

    private fun view() = checkNotNull(review.view.value)

    private fun open(vararg pairs: ReorgDuplicate) = review.open(pairs.toList())

    @Test
    fun `the review is closed until it is opened`() {
        assertNull(review.view.value)
    }

    @Test
    fun `opening it with no pair keeps it closed`() {
        review.open(emptyList())

        assertNull(review.view.value)
    }

    @Test
    fun `opening it ticks every file and starts in miniatures`() {
        open(pair("a.jpg"), pair("b.jpg"))

        assertEquals(listOf(true, true), view().rows.map { it.keepInput })
        assertEquals(listOf(true, true), view().rows.map { it.keepLibrary })
        assertEquals(DuplicateViewMode.MINIATURES, view().mode)
        assertEquals(0, view().trashCount)
        assertEquals("No file will go to the Trash", view().footer)
    }

    @Test
    fun `unticking an input file counts one file for the Trash`() {
        open(pair("a.jpg"), pair("b.jpg"))

        review.toggleInput(1)

        assertEquals(listOf(true, false), view().rows.map { it.keepInput })
        assertEquals(1, view().trashCount)
        assertEquals("1 file will go to the Trash", view().footer)
    }

    @Test
    fun `unticking both files of a row counts two and ticking one back counts one`() {
        open(pair("a.jpg"))

        review.toggleInput(0)
        review.toggleLibrary(0)
        assertEquals(2, view().trashCount)
        assertEquals("2 files will go to the Trash", view().footer)

        review.toggleLibrary(0)
        assertEquals(1, view().trashCount)
    }

    @Test
    fun `the input header unticks a full column, then ticks it again`() {
        open(pair("a.jpg"), pair("b.jpg"))
        assertTrue(view().inputColumnKept)

        review.toggleInputColumn()
        assertEquals(listOf(false, false), view().rows.map { it.keepInput })
        assertFalse(view().inputColumnKept)
        assertEquals(listOf(true, true), view().rows.map { it.keepLibrary })

        review.toggleInputColumn()
        assertEquals(listOf(true, true), view().rows.map { it.keepInput })
    }

    @Test
    fun `the input header ticks a mixed column`() {
        open(pair("a.jpg"), pair("b.jpg"))
        review.toggleInput(0)

        review.toggleInputColumn()

        assertEquals(listOf(true, true), view().rows.map { it.keepInput })
    }

    @Test
    fun `the library header works like the input header`() {
        open(pair("a.jpg"), pair("b.jpg"))
        assertTrue(view().libraryColumnKept)

        review.toggleLibraryColumn()
        assertEquals(listOf(false, false), view().rows.map { it.keepLibrary })
        assertFalse(view().libraryColumnKept)

        review.toggleLibrary(0)
        review.toggleLibraryColumn()
        assertEquals(listOf(true, true), view().rows.map { it.keepLibrary })
    }

    @Test
    fun `a row that is not there and a closed review are ignored`() {
        review.toggleInput(0)
        review.toggleLibrary(0)
        review.toggleInputColumn()
        review.toggleLibraryColumn()
        review.setMode(DuplicateViewMode.DETAILS)
        review.preview(pair("a.jpg").input)
        review.preview(null)
        assertNull(review.view.value)

        open(pair("a.jpg"))
        review.toggleInput(5)
        review.toggleLibrary(-1)

        assertEquals(0, view().trashCount)
    }

    @Test
    fun `switching the view keeps every choice and the next review opens in that view`() {
        open(pair("a.jpg"), pair("b.jpg"))
        review.toggleInput(0)

        review.setMode(DuplicateViewMode.DETAILS)

        assertEquals(DuplicateViewMode.DETAILS, view().mode)
        assertEquals(listOf(false, true), view().rows.map { it.keepInput })
        review.cancel()
        open(pair("c.jpg"))
        assertEquals(DuplicateViewMode.DETAILS, view().mode)
        assertEquals(listOf(true), view().rows.map { it.keepInput })
    }

    @Test
    fun `a preview is opened on a file and closed again`() {
        val first = pair("a.jpg")
        open(first)

        review.preview(first.library)
        assertEquals(first.library, view().previewing)

        review.preview(null)
        assertNull(view().previewing)
    }

    @Test
    fun `confirm returns what is ticked and closes the review`() {
        val a = pair("a.jpg")
        val b = pair("b.jpg")
        open(a, b)
        review.toggleInput(0)
        review.toggleLibrary(1)

        val choices = review.confirm()

        assertEquals(listOf(DuplicateChoice(a, false, true), DuplicateChoice(b, true, false)), choices)
        assertNull(review.view.value)
    }

    @Test
    fun `confirm on a closed review returns nothing`() {
        assertNull(review.confirm())
    }

    @Test
    fun `cancel closes the review and there is nothing left to confirm`() {
        open(pair("a.jpg"))
        review.toggleInput(0)

        review.cancel()

        assertNull(review.view.value)
        assertNull(review.confirm())
    }

    @Test
    fun `the headers name the one input folder and the one year`() {
        open(pair("a.jpg"), pair("b.jpg"))

        assertEquals("Files from Camera", view().inputHeader)
        assertEquals("Files already in 2018", view().libraryHeader)
    }

    @Test
    fun `the headers stay general when pairs come from several folders and years`() {
        open(pair("a.jpg", source = "Camera", year = "2018"), pair("b.jpg", source = "WhatsApp", year = "2019"))

        assertEquals("Files from the input folders", view().inputHeader)
        assertEquals("Files already in the library", view().libraryHeader)
    }

    @Test
    fun `a library file with no year folder gives the general library header`() {
        open(pair("a.jpg", year = ""))

        assertEquals("Files already in the library", view().libraryHeader)
    }
}
