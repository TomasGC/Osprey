package app.osprey.actions.generated

import app.osprey.actions.dates.DatesPlanner
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.standardize.StandardizePlanner
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileTimestamps
import app.osprey.settings.InputMode
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promise of every planner (#129): applying a plan and planning again finds nothing, and no file is lost or
 * overwritten. Each test runs one property over 300 generated mixes of names, metadata and shooting offsets; a
 * failure prints its seed and the smallest mix that still fails (see [GeneratedMixes]).
 */
class PlannerIdempotenceTest {

    @Test
    fun `Standardize planned again after it ran finds nothing, loses nothing and overwrites nothing`() =
        GeneratedMixes.check { mix ->
            val lib = MixedFolder().also { it.addSpread(mix) }
            val before = lib.contents()
            val planner = StandardizePlanner(lib.fs, lib.reader)
            val first = planner.plan(lib.root)

            val renamed = planner.apply(first)
            val second = planner.plan(lib.root)

            assertEquals("files renamed", first.renames.size, renamed)
            assertEquals(
                "renames planned again",
                emptyList<String>(),
                second.renames.map { "${it.sourceName} -> ${it.targetName}" },
            )
            assertEquals(
                "unfixable",
                first.unfixable.map { it.handle }.toSet(),
                second.unfixable.map { it.handle }.toSet(),
            )
            assertEquals("contents", before, lib.contents())
        }

    @Test
    fun `Dates planned again after it ran gives the same plan and leaves every time as it was`() =
        GeneratedMixes.check { mix ->
            val lib = MixedFolder()
            val handles = lib.addSpread(mix)
            val before = lib.contents()
            val planner = DatesPlanner(lib.fs, lib.reader)
            val first = planner.plan(lib.root)

            val fixed = planner.apply(first)
            val stamps = lib.timestamps(handles)
            val second = planner.plan(lib.root)

            assertEquals("files dated", first.fixes.size, fixed)
            assertEquals("fixes planned again", first.fixes, second.fixes)
            assertEquals("unfixable", first.unfixable, second.unfixable)
            first.fixes.forEach {
                val expected = it.dateTime.atZone(ZoneId.systemDefault()).toInstant()
                assertEquals("modified time of ${it.name}", expected, lib.fs.readTimestamps(it.entry).modified)
            }
            planner.apply(second)
            assertEquals("timestamps after a second apply", stamps, lib.timestamps(handles))
            assertEquals("contents", before, lib.contents())
        }

    @Test
    fun `Dates then Standardize and Standardize then Dates leave the same times`() = GeneratedMixes.check { mix ->
        assertEquals(timestampsAfter(mix, standardizeFirst = false), timestampsAfter(mix, standardizeFirst = true))
    }

    private fun timestampsAfter(mix: List<FileSpec>, standardizeFirst: Boolean): Map<Int, FileTimestamps> {
        val lib = MixedFolder()
        val handles = lib.addSpread(mix)
        val dates = DatesPlanner(lib.fs, lib.reader)
        val standardize = StandardizePlanner(lib.fs, lib.reader)
        val runDates = { dates.apply(dates.plan(lib.root)) }
        val runStandardize = { standardize.apply(standardize.plan(lib.root)) }
        if (standardizeFirst) {
            runStandardize()
            runDates()
        } else {
            runDates()
            runStandardize()
        }
        return lib.timestamps(handles)
    }

    @Test
    fun `Reorg gives every file the same year before and after Standardize`() = GeneratedMixes.check { mix ->
        val lib = MixedFolder().also { it.addAll(mix) }
        val reorg = ReorgPlanner(lib.fs, lib.reader)
        val standardize = StandardizePlanner(lib.fs, lib.reader)
        // Every file sits in the output's root, so each dated one is planned for a year folder.
        fun yearsAndUndated(): Pair<Map<FileHandle, Int>, Int> {
            val plan = reorg.plan(lib.root, emptyList())
            return plan.items.associate { it.source to it.year } to plan.undated.size
        }
        val before = yearsAndUndated()

        standardize.apply(standardize.plan(lib.root))

        assertEquals("year of each file, and how many are undated", before, yearsAndUndated())
    }

    @Test
    fun `Reorg planned again after it ran finds nothing, loses nothing and overwrites nothing`() =
        GeneratedMixes.check { mix ->
            val lib = MixedFolder()
            val output = lib.fs.addFolder(lib.root, "Library")
            val misfiled = lib.fs.addFolder(output, "2020")
            val camera = lib.fs.addFolder(lib.root, "Camera")
            val whatsapp = lib.fs.addFolder(lib.root, "WhatsApp")
            val places = listOf(output, misfiled, camera, whatsapp)
            mix.forEach { lib.add(places[it.id % places.size], it) }
            val sources = listOf(
                ReorgSource(camera, "Camera", InputMode.COPY),
                ReorgSource(whatsapp, "WhatsApp", InputMode.MOVE),
            )
            val before = lib.contents()
            val planner = ReorgPlanner(lib.fs, lib.reader)
            val first = planner.plan(output, sources)

            val result = planner.apply(first, output)
            lib.readMetadataFromContent(mix)
            val second = planner.plan(output, sources)
            val after = lib.contents()

            assertTrue("contents are unique, so no pair is a duplicate", first.duplicates.isEmpty())
            assertEquals("failures", emptyList<Any>(), result.failures)
            assertEquals("items planned again", emptyList<String>(), second.items.map { it.label })
            assertEquals("undated", first.undated.toSet(), second.undated.toSet())
            assertTrue("every content is still there", after.toSet().containsAll(before))
            assertEquals("files", before.size + result.copied, after.size)
        }

    @Test
    fun `Dates and Reorg give every file whose date is clear that date, or none when it has no date`() =
        GeneratedMixes.check { mix ->
            val lib = MixedFolder()
            assertDatesAsExpected(lib, mix, lib.addAll(mix))
        }

    @Test
    fun `a file keeps its date after Standardize renamed it`() = GeneratedMixes.check { mix ->
        val lib = MixedFolder()
        val handles = lib.addAll(mix)
        val standardize = StandardizePlanner(lib.fs, lib.reader)

        standardize.apply(standardize.plan(lib.root))

        assertDatesAsExpected(lib, mix, handles)
    }

    // The dates are checked against what each spec stands for (FileSpec.expectedDate), not against another run.
    private fun assertDatesAsExpected(lib: MixedFolder, mix: List<FileSpec>, handles: Map<Int, FileHandle>) {
        val dates = DatesPlanner(lib.fs, lib.reader).plan(lib.root)
        val fixed = dates.fixes.associate { it.entry to it.dateTime }
        val unfixable = dates.unfixable.map { it.handle }.toSet()
        val reorg = ReorgPlanner(lib.fs, lib.reader).plan(lib.root, emptyList())
        val years = reorg.items.associate { it.source to it.year }
        mix.forEach { spec ->
            val handle = handles.getValue(spec.id)
            when (val expected = spec.expectedDate()) {
                ExpectedDate.Unclear -> Unit
                ExpectedDate.Undated -> {
                    assertTrue("${spec.describe()}: Dates should list it unfixable", handle in unfixable)
                    assertFalse("${spec.describe()}: Reorg should not file it", handle in years)
                }
                is ExpectedDate.At -> {
                    assertEquals("${spec.describe()}: Dates time", expected.time, fixed[handle])
                    assertEquals("${spec.describe()}: Reorg year", expected.time.year, years[handle])
                }
            }
        }
    }
}
