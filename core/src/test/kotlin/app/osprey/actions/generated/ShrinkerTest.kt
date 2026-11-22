package app.osprey.actions.generated

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ShrinkerTest {

    private val exif = SpecMetadata.Exif(LocalDateTime.of(2020, 1, 1, 12, 0))

    private fun spec(id: Int, metadata: SpecMetadata = SpecMetadata.None) = FileSpec(id, "f$id.jpg", metadata)

    @Test
    fun `drops every file the failure does not need`() {
        val mix = (1..6).map { spec(it) }

        val smallest = shrink(mix) { files -> files.any { it.id == 4 } }

        assertEquals(listOf(spec(4)), smallest)
    }

    @Test
    fun `keeps both files of a failure that needs two`() {
        val mix = (1..6).map { spec(it) }

        val smallest = shrink(mix) { files -> files.any { it.id == 2 } && files.any { it.id == 5 } }

        assertEquals(listOf(spec(2), spec(5)), smallest)
    }

    @Test
    fun `clears the metadata a failure does not need and keeps the metadata it needs`() {
        val mix = listOf(spec(1, exif), spec(2, exif), spec(3, exif))

        val smallest = shrink(mix) { files -> files.any { it.id == 1 && it.metadata == exif } }

        assertEquals(listOf(spec(1, exif)), smallest)
    }

    @Test
    fun `clears the metadata of a file it has to keep`() {
        val mix = listOf(spec(1, exif), spec(2, exif))

        val smallest = shrink(mix) { files -> files.size == 2 }

        assertEquals(listOf(spec(1), spec(2)), smallest)
    }

    @Test
    fun `returns the same mix when nothing smaller fails`() {
        val mix = listOf(spec(1, exif), spec(2, exif))

        val smallest = shrink(mix) { files -> files == mix }

        assertEquals(mix, smallest)
    }

    @Test
    fun `never returns an empty mix`() {
        val smallest = shrink(listOf(spec(1), spec(2))) { true }

        assertEquals(1, smallest.size)
    }
}
