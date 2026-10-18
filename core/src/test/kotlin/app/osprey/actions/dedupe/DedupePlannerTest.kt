package app.osprey.actions.dedupe

import app.osprey.filesystem.FakeFileSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupePlannerTest {

    @Test
    fun `plan finds nothing when no files duplicate`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a.jpg", "content one".toByteArray())
        fs.addFile(fs.root, "b.jpg", "content two".toByteArray())
        val planner = DedupePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<DedupeMove>(), plan.moves)
    }

    @Test
    fun `plan does not flag same-size files with different content`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a.jpg", "aaaaa".toByteArray())
        fs.addFile(fs.root, "b.jpg", "bbbbb".toByteArray())
        val planner = DedupePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<DedupeMove>(), plan.moves)
    }

    @Test
    fun `plan keeps the shortest, then alphabetically-first, name`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "zzzzz.jpg", "dup content".toByteArray())
        fs.addFile(fs.root, "a.jpg", "dup content".toByteArray())
        fs.addFile(fs.root, "bb.jpg", "dup content".toByteArray())
        val planner = DedupePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(2, plan.moves.size)
        assertEquals(setOf("a.jpg"), plan.moves.map { it.keepName }.toSet())
        assertEquals(setOf("zzzzz.jpg", "bb.jpg"), plan.moves.map { it.sourceName }.toSet())
    }

    @Test
    fun `plan groups a three-way duplicate together`() {
        val fs = FakeFileSystemRepository()
        for (name in listOf("a.jpg", "b.jpg", "c.jpg")) {
            fs.addFile(fs.root, name, "same".toByteArray())
        }
        val planner = DedupePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(2, plan.moves.size)
        assertTrue(plan.moves.all { it.keepName == "a.jpg" })
    }

    @Test
    fun `plan suffixes a destination name already taken in TODELETE`() {
        val fs = FakeFileSystemRepository()
        val todelete = fs.addFolder(fs.root, "TODELETE")
        fs.addFile(todelete, "b.jpg", "pre-existing".toByteArray())
        fs.addFile(fs.root, "a.jpg", "dup content".toByteArray())
        fs.addFile(fs.root, "b.jpg", "dup content".toByteArray())
        val planner = DedupePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(1, plan.moves.size)
        assertEquals("b__dup1.jpg", plan.moves.single().destName)
    }

    @Test
    fun `apply moves extras into TODELETE and keeps the original`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a.jpg", "dup content".toByteArray())
        fs.addFile(fs.root, "b.jpg", "dup content".toByteArray())
        val planner = DedupePlanner(fs)
        val plan = planner.plan(fs.root)

        planner.apply(plan, fs.root)

        assertTrue(fs.list(fs.root).any { it.name == "a.jpg" })
        assertFalse(fs.list(fs.root).any { it.name == "b.jpg" })
        val todelete = fs.list(fs.root).single { it.name == "TODELETE" }
        assertEquals(listOf("b.jpg"), fs.list(todelete.handle).map { it.name })
    }

    @Test
    fun `apply on a no-op plan never creates TODELETE`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a.jpg", "unique".toByteArray())
        val planner = DedupePlanner(fs)
        val plan = planner.plan(fs.root)

        planner.apply(plan, fs.root)

        assertFalse(fs.list(fs.root).any { it.name == "TODELETE" })
    }

    @Test
    fun `describe logs each move and counts them`() {
        val fs = FakeFileSystemRepository()
        val planner = DedupePlanner(fs)
        val plan = DedupePlan(
            moves = listOf(DedupeMove(fs.addFile(fs.root, "b.jpg"), "b.jpg", "TODELETE/b.jpg", "a.jpg")),
        )
        val logs = mutableListOf<String>()

        val count = planner.describe(plan, logs::add)

        assertEquals(1, count)
        assertTrue(logs.single().contains("dup of a.jpg"))
    }
}
