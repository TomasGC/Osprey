package app.osprey.actions.visual

import app.osprey.filesystem.FakeFileSystemRepository
import app.osprey.media.FakeMediaMetadataReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualDupePlannerTest {

    @Test
    fun `plan groups near-identical images together`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val a = fs.addFile(fs.root, "a.jpg")
        val b = fs.addFile(fs.root, "b.jpg")
        val different = fs.addFile(fs.root, "different.jpg")
        reader.setFrame(a, noisePixelBuffer(seed = 1))
        reader.setFrame(b, noisePixelBuffer(seed = 1))
        reader.setFrame(different, noisePixelBuffer(seed = 2))
        val planner = VisualDupePlanner(fs, reader)

        val plan = planner.plan(fs.root)

        assertEquals(1, plan.groups.size)
        assertEquals(setOf("a.jpg", "b.jpg"), plan.groups.single().map { it.name }.toSet())
    }

    @Test
    fun `plan orders group members heaviest-first`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val small = fs.addFile(fs.root, "small.jpg", ByteArray(10))
        val large = fs.addFile(fs.root, "large.jpg", ByteArray(5000))
        reader.setFrame(small, noisePixelBuffer(seed = 1))
        reader.setFrame(large, noisePixelBuffer(seed = 1))
        val planner = VisualDupePlanner(fs, reader)

        val plan = planner.plan(fs.root)

        assertEquals(1, plan.groups.size)
        assertEquals("large.jpg", plan.groups.single().first().name)
    }

    @Test
    fun `plan ignores files inside TODELETE`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val a = fs.addFile(fs.root, "a.jpg")
        reader.setFrame(a, noisePixelBuffer(seed = 1))
        val todelete = fs.addFolder(fs.root, "TODELETE")
        val b = fs.addFile(todelete, "b.jpg")
        reader.setFrame(b, noisePixelBuffer(seed = 1))
        val planner = VisualDupePlanner(fs, reader)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<List<VisualDupeMember>>(), plan.groups)
    }

    @Test
    fun `plan never unions two files sharing the same burst id`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val burst0 = fs.addFile(fs.root, "DSCPDC_0000_BURST1.JPG")
        val burst1 = fs.addFile(fs.root, "DSCPDC_0001_BURST1.JPG")
        val buffer = noisePixelBuffer(seed = 1)
        reader.setFrame(burst0, buffer)
        reader.setFrame(burst1, buffer)
        val planner = VisualDupePlanner(fs, reader)

        val plan = planner.plan(fs.root)

        // Visually identical but same burst id -- never clustered as a dup group.
        assertEquals(emptyList<List<VisualDupeMember>>(), plan.groups)
    }

    @Test
    fun `plan does not group a genuinely different shot of the same scene as a dupe`() {
        // Issue #26: a real "3 photos of the same scene seconds apart" case clustered as a
        // false-positive dupe group under the old, looser threshold. A small re-randomized
        // fraction of pixels stands in for that -- close enough to look similar, too far to be
        // the same capture re-exported.
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val base = noisePixelBuffer(seed = 1)
        val a = fs.addFile(fs.root, "a.jpg")
        val b = fs.addFile(fs.root, "different_moment.jpg")
        reader.setFrame(a, base)
        reader.setFrame(b, perturbedPixelBuffer(base, seed = 42))
        val planner = VisualDupePlanner(fs, reader)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<List<VisualDupeMember>>(), plan.groups)
    }

    @Test
    fun `plan excludes singleton clusters`() {
        val fs = FakeFileSystemRepository()
        val reader = FakeMediaMetadataReader()
        val a = fs.addFile(fs.root, "a.jpg")
        val b = fs.addFile(fs.root, "b.jpg")
        val c = fs.addFile(fs.root, "c.jpg")
        reader.setFrame(a, noisePixelBuffer(seed = 1))
        reader.setFrame(b, noisePixelBuffer(seed = 2))
        reader.setFrame(c, noisePixelBuffer(seed = 3))
        val planner = VisualDupePlanner(fs, reader)

        val plan = planner.plan(fs.root)

        assertEquals(emptyList<List<VisualDupeMember>>(), plan.groups)
    }

    @Test
    fun `describe logs a normal group with the heaviest kept`() {
        val fs = FakeFileSystemRepository()
        val planner = VisualDupePlanner(fs, FakeMediaMetadataReader())
        val a = VisualDupeMember(fs.addFile(fs.root, "a.jpg"), "a.jpg", 100)
        val b = VisualDupeMember(fs.addFile(fs.root, "b.jpg"), "b.jpg", 50)
        val plan = VisualDupePlan(groups = listOf(listOf(a, b)))
        val logs = mutableListOf<String>()

        val (groupCount, extraCount) = planner.describe(plan, logs::add)

        assertEquals(1, groupCount)
        assertEquals(1, extraCount)
        assertTrue(logs.any { it.contains("keep=a.jpg") })
        assertTrue(logs.any { it.contains("~dup b.jpg") })
    }

    @Test
    fun `describe keeps every frame of a burst set`() {
        val fs = FakeFileSystemRepository()
        val planner = VisualDupePlanner(fs, FakeMediaMetadataReader())
        val burst1 = VisualDupeMember(fs.addFile(fs.root, "DSCPDC_0000_BURST1.JPG"), "DSCPDC_0000_BURST1.JPG", 100)
        val burst2 = VisualDupeMember(fs.addFile(fs.root, "DSCPDC_0001_BURST1.JPG"), "DSCPDC_0001_BURST1.JPG", 90)
        val other = VisualDupeMember(fs.addFile(fs.root, "DSC_0815.JPG"), "DSC_0815.JPG", 80)
        val plan = VisualDupePlan(groups = listOf(listOf(burst1, burst2, other)))
        val logs = mutableListOf<String>()

        val (groupCount, extraCount) = planner.describe(plan, logs::add)

        assertEquals(1, groupCount)
        // Only the non-burst member counted as an extra.
        assertEquals(1, extraCount)
        assertTrue(logs.any { it.contains("BURST SET") })
        assertTrue(logs.any { it.contains("KEEP (burst frame)") })
        assertTrue(logs.any { it.contains("matches the burst") })
    }
}
