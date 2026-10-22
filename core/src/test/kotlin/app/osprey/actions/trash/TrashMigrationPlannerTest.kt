package app.osprey.actions.trash

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.filesystem.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashMigrationPlannerTest {

    private val fs = FakeFileSystemRepository()
    private val planner = TrashMigrationPlanner(fs)

    private fun yearFolder(year: String): FileHandle = fs.addFolder(fs.root, year)

    private fun child(parent: FileHandle, name: String): FileHandle =
        fs.list(parent).single { it.isDirectory && it.name == name }.handle

    private fun names(folder: FileHandle) = fs.list(folder).map { it.name }.toSet()

    private fun trashFolder(year: String, action: String) = child(child(child(fs.root, "Trash"), year), action)

    @Test
    fun `hasLegacy is false for a library without old trash folders`() {
        yearFolder("2019")

        assertFalse(planner.hasLegacy(fs.root))
    }

    @Test
    fun `hasLegacy is true while an old folder exists, even an empty one`() {
        fs.addFolder(yearFolder("2019"), "TODELETEDUPS")

        assertTrue(planner.hasLegacy(fs.root))
    }

    @Test
    fun `plan sends TODELETE to Dedupe and TODELETEDUPS to VisualDupes of the same year`() {
        val year = yearFolder("2019")
        fs.addFile(fs.addFolder(year, "TODELETE"), "a.jpg")
        fs.addFile(fs.addFolder(year, "TODELETEDUPS"), "b.jpg")

        val moves = planner.plan(fs.root).moves

        assertEquals(setOf("2019/TODELETE/a.jpg", "2019/TODELETEDUPS/b.jpg"), moves.map { it.sourcePath }.toSet())
        assertEquals(TrashAction.DEDUPE, moves.single { it.destName == "a.jpg" }.action)
        assertEquals(TrashAction.VISUAL_DUPES, moves.single { it.destName == "b.jpg" }.action)
        assertTrue(moves.all { it.year == "2019" })
    }

    @Test
    fun `plan ignores old-looking folders outside year folders`() {
        fs.addFile(fs.addFolder(fs.root, "TODELETE"), "root.jpg")
        fs.addFile(fs.addFolder(fs.addFolder(fs.root, "Trips"), "TODELETE"), "trip.jpg")

        assertTrue(planner.plan(fs.root).moves.isEmpty())
        assertFalse(planner.hasLegacy(fs.root))
    }

    @Test
    fun `plan suffixes a name already in the destination`() {
        val year = yearFolder("2019")
        fs.addFile(fs.addFolder(year, "TODELETE"), "a.jpg")
        val existing = TrashFolders(fs).ensure(TrashLocation(fs.root, "2019"), TrashAction.DEDUPE)
        fs.addFile(existing, "a.jpg", byteArrayOf(9))

        val move = planner.plan(fs.root).moves.single()

        assertEquals("a__dup1.jpg", move.destName)
        assertEquals("(renamed, name collision)", move.note)
    }

    @Test
    fun `plan does not move a subfolder of an old folder`() {
        val old = fs.addFolder(yearFolder("2019"), "TODELETE")
        fs.addFile(old, "a.jpg")
        fs.addFolder(old, "nested")

        val plan = planner.plan(fs.root)

        assertEquals(listOf("a.jpg"), plan.moves.map { it.destName })
    }

    @Test
    fun `apply moves every file, creates Trash and removes the emptied old folders`() {
        val year = yearFolder("2019")
        fs.addFile(fs.addFolder(year, "TODELETE"), "a.jpg", byteArrayOf(1))
        fs.addFile(fs.addFolder(year, "TODELETEDUPS"), "b.jpg", byteArrayOf(2))

        planner.apply(planner.plan(fs.root), fs.root) {}

        assertEquals(setOf("a.jpg"), names(trashFolder("2019", "Dedupe")))
        assertEquals(setOf("b.jpg"), names(trashFolder("2019", "VisualDupes")))
        assertTrue(names(year).isEmpty())
        assertFalse(planner.hasLegacy(fs.root))
    }

    @Test
    fun `apply overwrites nothing`() {
        val year = yearFolder("2019")
        fs.addFile(fs.addFolder(year, "TODELETE"), "a.jpg", byteArrayOf(1))
        fs.addFile(TrashFolders(fs).ensure(TrashLocation(fs.root, "2019"), TrashAction.DEDUPE), "a.jpg", byteArrayOf(9))

        planner.apply(planner.plan(fs.root), fs.root) {}

        val dedupe = trashFolder("2019", "Dedupe")
        assertEquals(setOf("a.jpg", "a__dup1.jpg"), names(dedupe))
        assertEquals(9, fs.readBytes(fs.list(dedupe).single { it.name == "a.jpg" }.handle).single().toInt())
    }

    @Test
    fun `apply leaves an old folder that still holds a subfolder and logs it`() {
        val old = fs.addFolder(yearFolder("2019"), "TODELETE")
        fs.addFile(old, "a.jpg")
        fs.addFolder(old, "nested")
        val logs = mutableListOf<String>()

        planner.apply(planner.plan(fs.root), fs.root, logs::add)

        assertEquals(setOf("nested"), names(old))
        assertTrue(logs.single().contains("2019/TODELETE"))
    }

    @Test
    fun `apply removes an old folder that was already empty without creating Trash`() {
        val year = yearFolder("2019")
        fs.addFolder(year, "TODELETEDUPS")

        planner.apply(planner.plan(fs.root), fs.root) {}

        assertTrue(names(year).isEmpty())
        assertTrue(fs.list(fs.root).none { it.name == "Trash" })
    }

    @Test
    fun `a second run plans nothing`() {
        val year = yearFolder("2019")
        fs.addFile(fs.addFolder(year, "TODELETE"), "a.jpg")
        planner.apply(planner.plan(fs.root), fs.root) {}

        val second = planner.plan(fs.root)

        assertTrue(second.moves.isEmpty())
        assertTrue(second.folders.isEmpty())
    }

    @Test
    fun `describe logs one line per move and returns the count`() {
        val year = yearFolder("2019")
        fs.addFile(fs.addFolder(year, "TODELETE"), "a.jpg")
        val logs = mutableListOf<String>()

        val count = planner.describe(planner.plan(fs.root), logs::add)

        assertEquals(1, count)
        assertEquals("  TRASH 2019/TODELETE/a.jpg -> Trash/2019/Dedupe/a.jpg", logs.single())
    }
}
