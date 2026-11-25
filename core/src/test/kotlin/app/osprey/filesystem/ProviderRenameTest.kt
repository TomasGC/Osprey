package app.osprey.filesystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One document of a provider that behaves like Android's shared storage (#132): asked for a name already taken,
 * ignoring case and counting the document's own, it gives `name (1)` and says it worked. [refused] names fail.
 */
private class ProviderDocument(var name: String, private val others: Set<String> = emptySet()) {
    val refused = mutableSetOf<String>()
    val numbered = mutableSetOf<String>()
    val asked = mutableListOf<String>()

    fun rename(newName: String): Boolean {
        asked += newName
        if (newName in refused) return false
        val taken = newName in numbered || (others + name).any { it.equals(newName, ignoreCase = true) }
        name = if (taken) "${newName.substringBeforeLast('.')} (1).${newName.substringAfterLast('.')}" else newName
        return true
    }

    val renaming = ProviderRename({ name }, ::rename)
}

class ProviderRenameTest {

    @Test
    fun `a free name is asked for once`() {
        val document = ProviderDocument("a.jpg")

        document.renaming.to("b.jpg")

        assertEquals("b.jpg", document.name)
        assertEquals(listOf("b.jpg"), document.asked)
    }

    @Test
    fun `a name that differs only by case goes through a temporary name that keeps the extension`() {
        val document = ProviderDocument("img_a.jpg")

        document.renaming.to("IMG_a.jpg")

        assertEquals("IMG_a.jpg", document.name)
        assertEquals(2, document.asked.size)
        assertTrue(document.asked[0], document.asked[0].startsWith("IMG_a.osprey-rename-"))
        assertTrue(document.asked[0], document.asked[0].endsWith(".jpg"))
    }

    @Test
    fun `the name it already has is asked once and is not a case change`() {
        val document = ProviderDocument("a.jpg")

        assertThrows(FileAlreadyExistsException::class.java) { document.renaming.to("a.jpg") }

        assertEquals(listOf("a.jpg", "a.jpg"), document.asked)
    }

    @Test
    fun `two temporary names differ`() {
        assertTrue(temporaryName("a.jpg") != temporaryName("a.jpg"))
    }

    @Test
    fun `another name than the one asked for is undone and reported`() {
        val document = ProviderDocument("a.jpg", others = setOf("B.JPG"))

        val failure = assertThrows(FileAlreadyExistsException::class.java) { document.renaming.to("b.jpg") }

        assertEquals("b.jpg is taken: the storage gave b (1).jpg instead", failure.message)
        assertEquals("a.jpg", document.name)
    }

    @Test
    fun `a refused rename is reported and changes nothing`() {
        val document = ProviderDocument("a.jpg")
        document.refused += "b.jpg"

        val failure = assertThrows(IllegalStateException::class.java) { document.renaming.to("b.jpg") }

        assertEquals("rename failed: a.jpg -> b.jpg", failure.message)
        assertEquals("a.jpg", document.name)
    }

    @Test
    fun `a file that cannot be given its name back is named in the failure`() {
        val document = ProviderDocument("img_a.jpg")
        document.refused += listOf("IMG_a.jpg", "img_a.jpg")

        val failure = assertThrows(IllegalStateException::class.java) { document.renaming.to("IMG_a.jpg") }

        val temporary = document.asked.first()
        assertEquals("rename failed: $temporary -> IMG_a.jpg; the file is left as $temporary", failure.message)
        assertEquals(temporary, document.name)
    }

    @Test
    fun `a second step answered with another name gives the file its name back`() {
        val document = ProviderDocument("img_a.jpg")
        document.numbered += "IMG_a.jpg"

        assertThrows(FileAlreadyExistsException::class.java) { document.renaming.to("IMG_a.jpg") }

        assertEquals("img_a.jpg", document.name)
    }

    @Test
    fun `a failed second step gives the file its name back`() {
        val document = ProviderDocument("img_a.jpg")
        document.refused += "IMG_a.jpg"

        assertThrows(IllegalStateException::class.java) { document.renaming.to("IMG_a.jpg") }

        assertEquals("img_a.jpg", document.name)
    }
}
