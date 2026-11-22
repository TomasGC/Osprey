package app.osprey.actions.generated

import org.junit.Assert.assertEquals
import org.junit.Test

class MixedFolderTest {

    private val mix = (0..3).map { FileSpec(it, "photo_$it.jpg", SpecMetadata.None) }

    @Test
    fun `a spread mix puts the odd ids in a subfolder and the others in the root`() {
        val lib = MixedFolder()

        lib.addSpread(mix)

        val entries = lib.fs.list(lib.root)
        val sub = entries.single { it.isDirectory }
        assertEquals(listOf("photo_0.jpg", "photo_2.jpg", "sub"), entries.map { it.name }.sorted())
        assertEquals(listOf("photo_1.jpg", "photo_3.jpg"), lib.fs.list(sub.handle).map { it.name }.sorted())
    }

    @Test
    fun `contents lists the files of every folder, sorted`() {
        val lib = MixedFolder()

        lib.addSpread(mix)

        assertEquals(mix.map { String(it.content) }.sorted(), lib.contents())
    }
}
