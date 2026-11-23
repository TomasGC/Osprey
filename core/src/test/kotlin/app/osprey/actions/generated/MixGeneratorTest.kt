package app.osprey.actions.generated

import app.osprey.actions.standardize.StandardizePlanner
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixGeneratorTest {

    private val mixes = GeneratedMixes.SEEDS.map { MixGenerator.mix(it) }
    private val specs = mixes.flatten()

    private fun anyName(pattern: String) = specs.any { Regex(pattern).containsMatchIn(it.name) }

    private val containers get() = specs.map { it.metadata }.filterIsInstance<SpecMetadata.Container>()

    private fun machineOffset(container: SpecMetadata.Container): Int =
        ZoneId.systemDefault().rules.getOffset(container.utc.toInstant(ZoneOffset.UTC)).totalSeconds

    @Test
    fun `one seed gives one mix and two seeds give two`() {
        assertEquals(MixGenerator.mix(42L), MixGenerator.mix(42L))
        assertNotEquals(MixGenerator.mix(42L), MixGenerator.mix(43L))
    }

    @Test
    fun `a mix is never empty and holds unique ids and unique names`() {
        mixes.forEach { mix ->
            assertTrue(mix.isNotEmpty())
            assertEquals(mix.size, mix.map { it.id }.toSet().size)
            assertEquals(mix.size, mix.map { it.name.lowercase() }.toSet().size)
        }
    }

    @Test
    fun `every naming convention of the name reader appears`() {
        listOf(
            """^IMG_\d{8}_\d{6}\.""", """^VID_\d{8}_\d{6}\.""", """^MOV_\d{8}_\d{6}\.""",
            """^Ultimate_HDR_Camera_Original_\d{8}_\d{6}\.""", """^AR_EFFECT_\d{14}\.""",
            """^download_\d{8}_\d{6}\.""", """^\d{8}_\d{6}\.""", """^Screenshot_\d{8}-\d{6}\.""",
            """^CameraZOOM-\d{14}\.""",
        ).forEach { assertTrue(it, anyName(it)) }
    }

    @Test
    fun `standardized names, collision copies, WhatsApp names, epochs, undated and impossible names appear`() {
        listOf(
            """^(IMG|VID)_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}\.""",
            """^(IMG|VID)_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}__dup\d\.""",
            """^(IMG|VID)-\d{8}-WA\d{4}\.""", """^\d{13}\.""", """^photo_\d+\.""", """^IMG_20201301_120000\.""",
            """^notes_\d+\.txt$""",
        ).forEach { assertTrue(it, anyName(it)) }
    }

    @Test
    fun `every kind of metadata appears`() {
        val kinds = specs.map { it.metadata::class }.toSet()

        assertEquals(
            setOf(
                SpecMetadata.None::class,
                SpecMetadata.Exif::class,
                SpecMetadata.Container::class,
                SpecMetadata.UnsetContainer::class,
            ),
            kinds,
        )
    }

    @Test
    fun `video containers cover offsets behind, ahead, the machine's own and a few minutes off it`() {
        val offsets = containers.map { it.offsetSeconds }

        assertTrue("behind UTC", offsets.any { it < 0 })
        assertTrue("ahead of UTC", offsets.any { it > 0 })
        assertTrue("a quarter hour", offsets.any { it % 900 == 0 })
        assertTrue("the machine's own", containers.any { it.offsetSeconds == machineOffset(it) })
        assertTrue("a few minutes off", containers.any { it.offsetSeconds - machineOffset(it) in 60..300 })
        assertTrue("within -12:00..+14:00", offsets.filter { it % 900 == 0 }.all { it in -43_200..50_400 })
    }

    @Test
    fun `container offsets reach both ends of -12 00 to +14 00 and never go past them`() {
        val wide = (0L until 4000L).flatMap { MixGenerator.mix(it) }
            .map { it.metadata }.filterIsInstance<SpecMetadata.Container>().map { it.offsetSeconds }

        assertTrue("-12:00 reached", -43_200 in wide)
        assertTrue("+14:00 reached", 50_400 in wide)
        assertTrue("nothing below -12:00 but the machine's own", wide.none { it < -43_200 })
    }

    @Test
    fun `most files have a clear expected date, some are undated, and few are unclear`() {
        val expected = specs.map { it.expectedDate() }
        val at = expected.count { it is ExpectedDate.At }
        val undated = expected.count { it == ExpectedDate.Undated }
        val unclear = expected.count { it == ExpectedDate.Unclear }

        assertTrue("dated files: $at of ${specs.size}", at > specs.size / 2)
        assertTrue("undated files: $undated", undated > specs.size / 10)
        assertTrue("unclear files: $unclear of ${specs.size}", unclear in 1..specs.size / 10)
    }

    @Test
    fun `a mix reaches the paths the planners' invariants guard`() {
        val renames = mixes.map { mix ->
            val lib = MixedFolder()
            val handles = lib.addAll(mix)
            val plan = StandardizePlanner(lib.fs, lib.reader).plan(lib.root)
            val noMetadata = mix.filter { it.metadata == SpecMetadata.None }.map { handles.getValue(it.id) }
            plan.renames to noMetadata
        }

        assertTrue("a collision number", renames.any { (plan, _) -> plan.any { "__dup" in it.targetName } })
        assertTrue("a rename of a file with no metadata", renames.any { (plan, none) -> plan.any { it.entry in none } })
    }
}
