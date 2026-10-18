package app.osprey.actions.flatten

import app.osprey.filesystem.FakeFileSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlattenPlannerTest {

    @Test
    fun `plan moves a nested file up to root`() {
        val fs = FakeFileSystemRepository()
        val month = fs.addFolder(fs.root, "06")
        fs.addFile(month, "a.jpg", byteArrayOf(1))
        val planner = FlattenPlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(1, plan.moves.size)
        val move = plan.moves.single()
        assertEquals("06/a.jpg", move.sourcePath)
        assertEquals("a.jpg", move.destName)
        assertEquals("", move.note)
        assertEquals(listOf("06"), plan.folders.map { it.relativePath })
    }

    @Test
    fun `plan lists the deepest folder first`() {
        val fs = FakeFileSystemRepository()
        val month = fs.addFolder(fs.root, "06")
        val day = fs.addFolder(month, "15")
        fs.addFile(day, "a.jpg")
        fs.addFile(month, "b.jpg")
        val planner = FlattenPlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(listOf("06/15", "06"), plan.folders.map { it.relativePath })
        assertEquals(setOf("a.jpg", "b.jpg"), plan.moves.map { it.destName }.toSet())
    }

    @Test
    fun `plan skips TODELETE entirely`() {
        val fs = FakeFileSystemRepository()
        val todelete = fs.addFolder(fs.root, "TODELETE")
        fs.addFile(todelete, "dup.jpg")
        val planner = FlattenPlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<FlattenMove>(), plan.moves)
        assertEquals(emptyList<FlattenFolder>(), plan.folders)
    }

    @Test
    fun `plan suffixes a name collision with __flat1`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a.jpg", byteArrayOf(1))
        val month = fs.addFolder(fs.root, "06")
        fs.addFile(month, "a.jpg", byteArrayOf(2))
        val planner = FlattenPlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(1, plan.moves.size)
        val move = plan.moves.single()
        assertEquals("a__flat1.jpg", move.destName)
        assertTrue(move.note.contains("renamed"))
    }

    @Test
    fun `plan gives two colliding files distinct suffixes`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a.jpg", byteArrayOf(1))
        val june = fs.addFolder(fs.root, "06")
        fs.addFile(june, "a.jpg", byteArrayOf(2))
        val july = fs.addFolder(fs.root, "07")
        fs.addFile(july, "a.jpg", byteArrayOf(3))
        val planner = FlattenPlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(listOf("a__flat1.jpg", "a__flat2.jpg"), plan.moves.map { it.destName }.sorted())
    }

    @Test
    fun `apply moves files and removes the now-empty folders`() {
        val fs = FakeFileSystemRepository()
        val month = fs.addFolder(fs.root, "06")
        fs.addFile(month, "a.jpg")
        val planner = FlattenPlanner(fs)
        val plan = planner.plan(fs.root)
        val logs = mutableListOf<String>()

        planner.apply(plan, fs.root, logs::add)

        assertEquals(listOf("a.jpg"), fs.list(fs.root).map { it.name })
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `apply logs and keeps the folder when it is no longer empty`() {
        val fs = FakeFileSystemRepository()
        val month = fs.addFolder(fs.root, "06")
        fs.addFile(month, "a.jpg")
        val planner = FlattenPlanner(fs)
        val plan = planner.plan(fs.root)
        // Simulates a file appearing after the plan was made, outside the plan's knowledge.
        fs.addFile(month, "leftover.tmp")
        val logs = mutableListOf<String>()

        planner.apply(plan, fs.root, logs::add)

        assertEquals(1, logs.size)
        assertFalse(fs.list(fs.root).none { it.name == "06" })
    }

    @Test
    fun `describe counts the moves and logs one line per move`() {
        val fs = FakeFileSystemRepository()
        val month = fs.addFolder(fs.root, "06")
        fs.addFile(month, "a.jpg")
        val planner = FlattenPlanner(fs)
        val plan = planner.plan(fs.root)
        val logs = mutableListOf<String>()

        val count = planner.describe(plan, logs::add)

        assertEquals(1, count)
        assertEquals(1, logs.size)
        assertTrue(logs.single().contains("a.jpg"))
    }
}
