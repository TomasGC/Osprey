package app.osprey.desktop

import app.osprey.actions.dates.DatesPlanner
import app.osprey.actions.generated.candidates
import app.osprey.actions.generated.FileSpec
import app.osprey.actions.generated.GeneratedMixes
import app.osprey.actions.generated.MixGenerator
import app.osprey.actions.generated.asMediaDate
import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.actions.standardize.StandardizePlanner
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.testing.TimedTest
import app.osprey.settings.InputMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import kotlin.io.path.isRegularFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SEED_COUNT = 5
private const val MIN_FILES = 6
private const val CONTENT_PREFIX = "spec-"

/**
 * The invariants of [app.osprey.actions.generated.GeneratedMixes] (#129) on real files: a few seeds, each a mix with
 * at least six files, through the real controllers, job queue and history for Standardize and Dates, and through the
 * planner on the real file system for Reorg. The fake reader keys metadata by path, so it is registered again after
 * every rename or copy, as a real reader would read it from the file itself.
 */
class GeneratedMixesIntegrationMockTest : TimedTest() {

    private val seeds = GeneratedMixes.SEEDS.filter { MixGenerator.mix(it).size >= MIN_FILES }.take(SEED_COUNT)

    private fun withHarness(block: (ActionHarness) -> Unit) {
        val harness = ActionHarness()
        try {
            block(harness)
        } finally {
            harness.delete()
        }
    }

    private fun write(folder: Path, spec: FileSpec) {
        Files.createDirectories(folder)
        Files.write(folder.resolve(spec.name), spec.content)
    }

    /** Gives every generated file under the harness the metadata of the spec whose content it holds. */
    private fun register(harness: ActionHarness, mix: List<FileSpec>) {
        val byContent = mix.associateBy { String(it.content) }
        generatedFiles(harness).forEach { file ->
            byContent[String(Files.readAllBytes(file))]?.metadata?.asMediaDate()
                ?.let { harness.reader.setCaptureDateTime(PathHandle(file), it) }
        }
    }

    private fun generatedFiles(harness: ActionHarness): List<Path> =
        Files.walk(harness.root).use { paths ->
            paths.filter { it.isRegularFile() && String(Files.readAllBytes(it)).startsWith(CONTENT_PREFIX) }.toList()
        }

    private fun contents(harness: ActionHarness): List<String> =
        generatedFiles(harness).map { String(Files.readAllBytes(it)) }.sorted()

    private fun yearNames(harness: ActionHarness): List<String> =
        Files.list(harness.year).use { entries -> entries.map { it.fileName.toString() }.toList() }.sorted()

    @Test
    fun `Standardize run twice on real files finds nothing the second time and keeps every file`() =
        GeneratedMixes.check(seeds) { mix ->
            withHarness { harness ->
                mix.forEach { write(harness.year, it) }
                register(harness, mix)
                val before = contents(harness)
                val planner = StandardizePlanner(harness.fs, harness.reader)
                val unfixable = planner.plan(harness.yearHandle()).unfixable.map { it.name }.toSet()

                val names = (1..2).map {
                    val launch = harness.launch()
                    launch.scanAndApply(
                        standardizeController(harness.fs, harness.reader, launch.jobs) { harness.libraryLabel },
                    )
                    register(harness, mix)
                    yearNames(harness)
                }

                val third = planner.plan(harness.yearHandle())
                assertEquals("names after the first and the second run", names[0], names[1])
                assertEquals(
                    "renames planned again",
                    emptyList<String>(),
                    third.renames.map { "${it.sourceName} -> ${it.targetName}" },
                )
                assertEquals("unfixable", unfixable, third.unfixable.map { it.name }.toSet())
                assertEquals("contents", before, contents(harness))
                mix.forEach { spec ->
                    val file = generatedFiles(harness).single { String(Files.readAllBytes(it)) == String(spec.content) }
                    val name = file.fileName.toString()
                    val starts = spec.expectedDate().candidates().map(spec::standardizedStart)
                    if (starts.isEmpty()) {
                        assertEquals("${spec.describe()}: an undated file keeps its name", spec.name, name)
                    } else {
                        val wanted = starts.joinToString(" or ")
                        val message = "${spec.describe()}: named $name, expected a name starting $wanted"
                        assertTrue(message, starts.any(name::startsWith))
                    }
                }
            }
        }

    @Test
    fun `Dates run twice on real files sets every file to its date and changes nothing the second time`() =
        GeneratedMixes.check(seeds) { mix ->
            withHarness { harness ->
                mix.forEach { write(harness.year, it) }
                register(harness, mix)
                val before = contents(harness)
                val planner = DatesPlanner(harness.fs, harness.reader)

                val stamps = (1..2).map {
                    val launch = harness.launch()
                    launch.scanAndApply(
                        datesController(harness.fs, harness.reader, launch.jobs) { harness.libraryLabel },
                    )
                    modifiedTimes(harness)
                }

                assertEquals("modified times after the first and the second run", stamps[0], stamps[1])
                planner.plan(harness.yearHandle()).fixes.forEach {
                    val expected = it.dateTime.atZone(ZoneId.systemDefault()).toInstant()
                    val file = (it.entry as PathHandle).path
                    assertEquals("modified time of ${it.name}", expected, Files.getLastModifiedTime(file).toInstant())
                }
                assertEquals("contents", before, contents(harness))
                mix.forEach { spec ->
                    val candidates = spec.expectedDate().candidates()
                    if (candidates.isEmpty()) return@forEach
                    val modified = Files.getLastModifiedTime(harness.year.resolve(spec.name)).toInstant()
                    val message = "modified time $modified of ${spec.describe()}, expected one of $candidates"
                    assertTrue(message, modified in candidates.map { it.atZone(ZoneId.systemDefault()).toInstant() })
                }
            }
        }

    private fun modifiedTimes(harness: ActionHarness) =
        generatedFiles(harness).associate { it.fileName.toString() to Files.getLastModifiedTime(it).toInstant() }

    @Test
    fun `Reorg planned again on real files finds nothing and keeps every file`() = GeneratedMixes.check(seeds) { mix ->
        withHarness { harness ->
            val output = harness.library
            val camera = harness.root.resolve("Camera")
            val whatsapp = harness.root.resolve("WhatsApp")
            val places = listOf(output, output.resolve("2020"), camera, whatsapp)
            mix.forEach { write(places[it.id % places.size], it) }
            register(harness, mix)
            val before = contents(harness)
            val sources = listOf(
                ReorgSource(PathHandle(camera), "Camera", InputMode.COPY),
                ReorgSource(PathHandle(whatsapp), "WhatsApp", InputMode.MOVE),
            )
            val planner = ReorgPlanner(harness.fs, harness.reader)
            val first = planner.plan(PathHandle(output), sources)

            val result = planner.apply(first, PathHandle(output))
            register(harness, mix)
            val second = planner.plan(PathHandle(output), sources)
            val after = contents(harness)

            assertTrue("contents are unique, so no pair is a duplicate", first.duplicates.isEmpty())
            assertEquals("failures", emptyList<Any>(), result.failures)
            assertEquals("items planned again", emptyList<String>(), second.items.map { it.label })
            assertTrue("every content is still there", after.toSet().containsAll(before))
            assertEquals("files", before.size + result.copied, after.size)
            mix.forEach { spec ->
                val years = spec.expectedDate().candidates().map { it.year.toString() }
                if (years.isEmpty()) return@forEach
                val message = "year folder of ${spec.describe()}, expected one of $years"
                assertTrue(message, yearFolderOf(harness, spec) in years)
            }
        }
    }

    /** The name of the folder, under the library, that holds the file with this spec's content. */
    private fun yearFolderOf(harness: ActionHarness, spec: FileSpec): String? =
        Files.walk(harness.library).use { paths ->
            paths.filter { it.isRegularFile() && String(Files.readAllBytes(it)) == String(spec.content) }
                .map { it.parent.fileName.toString() }.toList().singleOrNull()
        }
}
