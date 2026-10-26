package app.osprey.actions.rename

import app.osprey.jobs.RecordingJobControl
import app.osprey.filesystem.FakeFileSystemRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenamePlannerTest {

    @Test
    fun `plan renames a file with a simple suffix`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "photo (1).jpg")
        val planner = RenamePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<RenameAction>(), plan.skips)
        assertEquals(1, plan.renames.size)
        val rename = plan.renames.single()
        assertEquals("photo (1).jpg", rename.sourceName)
        assertEquals("photo.jpg", rename.targetName)
    }

    @Test
    fun `plan skips when the target name already exists`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "photo.jpg")
        fs.addFile(fs.root, "photo (1).jpg")
        val planner = RenamePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<RenameAction>(), plan.renames)
        assertEquals(1, plan.skips.size)
        assertEquals("photo (1).jpg", plan.skips.single().sourceName)
    }

    @Test
    fun `plan skips the second of two colliding candidates`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "photo (1).jpg")
        fs.addFile(fs.root, "photo (2).jpg")
        val planner = RenamePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(1, plan.renames.size)
        assertEquals(1, plan.skips.size)
        assertEquals("photo (1).jpg", plan.renames.single().sourceName)
        assertEquals("photo (2).jpg", plan.skips.single().sourceName)
    }

    @Test
    fun `plan ignores directories`() {
        val fs = FakeFileSystemRepository()
        fs.addFolder(fs.root, "sub (1)")
        val planner = RenamePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<RenameAction>(), plan.renames)
        assertEquals(emptyList<RenameAction>(), plan.skips)
    }

    @Test
    fun `plan leaves clean names alone`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "DSC_0067.jpg")
        val planner = RenamePlanner(fs)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<RenameAction>(), plan.renames)
        assertEquals(emptyList<RenameAction>(), plan.skips)
    }

    @Test
    fun `apply renames on the repository`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "photo (1).jpg")
        val planner = RenamePlanner(fs)
        val plan = planner.plan(fs.root)

        planner.apply(plan)

        assertEquals(listOf("photo.jpg"), fs.list(fs.root).map { it.name })
    }

    @Test
    fun `describe logs renames and skips`() {
        val fs = FakeFileSystemRepository()
        val planner = RenamePlanner(fs)
        val plan = RenamePlan(
            renames = listOf(RenameAction(fs.addFile(fs.root, "a (1).jpg"), "a (1).jpg", "a.jpg")),
            skips = listOf(RenameAction(fs.addFile(fs.root, "b (1).jpg"), "b (1).jpg", "b.jpg")),
        )
        val logs = mutableListOf<String>()

        val count = planner.describe(plan, logs::add)

        assertEquals(1, count)
        assertTrue(logs.any { it.contains("SKIP") })
        assertTrue(logs.any { it.contains("RENAME") })
    }

    @Test
    fun `plan reports each file and no folder`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a (1).jpg")
        fs.addFile(fs.root, "b (1).jpg")
        fs.addFolder(fs.root, "sub")
        val control = RecordingJobControl()

        RenamePlanner(fs).plan(fs.root, control)

        assertEquals(listOf("a (1).jpg", "b (1).jpg"), control.reports.map { it.current })
        assertEquals(setOf(2), control.reports.map { it.total }.toSet())
    }

    @Test
    fun `plan stops when asked and keeps what it had`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a (1).jpg")
        fs.addFile(fs.root, "b (1).jpg")

        val plan = RenamePlanner(fs).plan(fs.root, RecordingJobControl(stopAfterReports = 1))

        assertEquals(listOf("a (1).jpg"), plan.renames.map { it.sourceName })
    }

    @Test
    fun `apply reports each rename and returns how many it made`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a (1).jpg")
        fs.addFile(fs.root, "b (1).jpg")
        val planner = RenamePlanner(fs)
        val control = RecordingJobControl()

        val renamed = planner.apply(planner.plan(fs.root), control)

        assertEquals(2, renamed)
        assertEquals(listOf("a (1).jpg", "b (1).jpg"), control.reports.map { it.current })
    }

    @Test
    fun `a stopped apply leaves the other files and a new scan finds them`() {
        val fs = FakeFileSystemRepository()
        fs.addFile(fs.root, "a (1).jpg")
        fs.addFile(fs.root, "b (1).jpg")
        val planner = RenamePlanner(fs)

        val renamed = planner.apply(planner.plan(fs.root), RecordingJobControl(stopAfterReports = 1))

        assertEquals(1, renamed)
        assertEquals(listOf("b (1).jpg"), planner.plan(fs.root).renames.map { it.sourceName })
    }
}
