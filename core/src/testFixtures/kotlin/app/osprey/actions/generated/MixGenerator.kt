package app.osprey.actions.generated

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** A generated name stem, the time it reads as, and whether it holds the clock of the place of shooting. */
private data class Stem(val text: String, val time: LocalDateTime?, val wallClock: Boolean)

private enum class NameKind {
    CAMERA, STANDARDIZED, DUP, WHATSAPP, EPOCH, NONE, IMPOSSIBLE, NOT_MEDIA
}

// Repeats weight the draw: camera names are the most common, a name with no date comes next.
private val KINDS = listOf(
    NameKind.CAMERA, NameKind.CAMERA, NameKind.CAMERA,
    NameKind.STANDARDIZED, NameKind.STANDARDIZED,
    NameKind.DUP, NameKind.WHATSAPP, NameKind.EPOCH,
    NameKind.NONE, NameKind.NONE, NameKind.IMPOSSIBLE, NameKind.NOT_MEDIA,
)

// One entry per naming convention the name reader knows; the prefix is part of the pattern.
private val CAMERA_FORMATS = listOf(
    "'IMG_'yyyyMMdd_HHmmss", "'VID_'yyyyMMdd_HHmmss", "'MOV_'yyyyMMdd_HHmmss",
    "'Ultimate_HDR_Camera_Original_'yyyyMMdd_HHmmss", "'AR_EFFECT_'yyyyMMddHHmmss",
    "'download_'yyyyMMdd_HHmmss", "yyyyMMdd_HHmmss", "'Screenshot_'yyyyMMdd-HHmmss",
    "'CameraZOOM-'yyyyMMddHHmmss",
).map(DateTimeFormatter::ofPattern)

private val STANDARDIZED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
private val DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

// Times the files of a mix are drawn from: a few close to a year edge or a clock change.
private val INSTANTS = listOf(
    LocalDateTime.of(2020, 7, 10, 8, 15, 0),
    LocalDateTime.of(2019, 12, 31, 23, 59, 30),
    LocalDateTime.of(2021, 1, 1, 0, 0, 10),
    LocalDateTime.of(2020, 3, 29, 1, 30, 0),
    LocalDateTime.of(2018, 10, 28, 2, 30, 0),
    LocalDateTime.of(2017, 4, 25, 15, 26, 2),
)

private val IMAGE_EXTENSIONS = listOf(".jpg", ".png", ".heic")
private val VIDEO_EXTENSIONS = listOf(".mp4", ".mov")

private const val POOL_SIZE = 3
private const val MIN_FILES = 2
private const val MAX_FILES = 10
private const val QUARTER_HOUR_SECONDS = 900
private const val MIN_OFFSET_STEPS = -48 // -12:00
private const val MAX_OFFSET_STEPS = 56 // +14:00
private const val FEW_MINUTES_FROM = 60
private const val FEW_MINUTES_TO = 300
private const val MAX_EXIF_SHIFT_MINUTES = 600L

/**
 * Mixes of files for the planners' invariant tests (#129): names of every convention, metadata of every kind,
 * and few enough distinct times that two files often resolve to the same second. The machine's zone is never
 * changed: a video's shooting offset varies instead, which covers every relation between it and the machine.
 * One seed gives one mix, always.
 */
object MixGenerator {

    fun mix(seed: Long): List<FileSpec> {
        val random = Random(seed)
        val pool = INSTANTS.shuffled(random).take(POOL_SIZE)
        val count = random.nextInt(MIN_FILES, MAX_FILES + 1)
        val seen = mutableSetOf<String>()
        return (0 until count).map { spec(it, random, pool) }.filter { seen.add(it.name.lowercase()) }
    }

    private fun spec(id: Int, random: Random, pool: List<LocalDateTime>): FileSpec {
        val kind = KINDS.random(random)
        if (kind == NameKind.NOT_MEDIA) return FileSpec(id, "notes_$id.txt", SpecMetadata.None)
        val video = random.nextInt(3) == 0
        val extension = (if (video) VIDEO_EXTENSIONS else IMAGE_EXTENSIONS).random(random)
        val instant = pool.random(random)
        val metadata = if (video) videoMetadata(random, instant) else photoMetadata(random, instant)
        // The name holds the clock of the place of shooting; for a container that is its UTC instant plus the offset.
        val wall = if (metadata is SpecMetadata.Container) {
            instant.plusSeconds(metadata.offsetSeconds.toLong())
        } else {
            instant
        }
        val stem = stem(kind, id, wall, video, random)
        return FileSpec(id, stem.text + extension, metadata, stem.time, stem.wallClock)
    }

    private fun photoMetadata(random: Random, instant: LocalDateTime): SpecMetadata = when {
        random.nextInt(3) == 0 -> SpecMetadata.None
        random.nextBoolean() -> SpecMetadata.Exif(instant)
        else -> SpecMetadata.Exif(instant.plusMinutes(random.nextLong(1, MAX_EXIF_SHIFT_MINUTES)))
    }

    private fun videoMetadata(random: Random, instant: LocalDateTime): SpecMetadata = when (random.nextInt(8)) {
        0, 1 -> SpecMetadata.None
        2 -> SpecMetadata.UnsetContainer
        3 -> SpecMetadata.Exif(instant)
        else -> SpecMetadata.Container(instant, offsetSeconds(random, instant))
    }

    // The machine's own offset, one a few minutes off it, or any quarter hour from -12:00 to +14:00.
    private fun offsetSeconds(random: Random, utc: LocalDateTime): Int {
        val machine = machineOffsetSeconds(utc)
        return when (random.nextInt(5)) {
            0 -> machine
            1 -> machine + random.nextInt(FEW_MINUTES_FROM, FEW_MINUTES_TO + 1)
            else -> random.nextInt(MIN_OFFSET_STEPS, MAX_OFFSET_STEPS + 1) * QUARTER_HOUR_SECONDS
        }
    }

    private fun stem(kind: NameKind, id: Int, wall: LocalDateTime, video: Boolean, random: Random): Stem {
        val prefix = if (video) "VID" else "IMG"
        return when (kind) {
            NameKind.CAMERA -> Stem(wall.format(CAMERA_FORMATS.random(random)), wall, true)
            NameKind.STANDARDIZED -> Stem("${prefix}_${wall.format(STANDARDIZED_FORMAT)}", wall, true)
            NameKind.DUP -> {
                val text = "${prefix}_${wall.format(STANDARDIZED_FORMAT)}__dup${random.nextInt(2, 5)}"
                Stem(text, wall, true)
            }
            NameKind.WHATSAPP -> {
                val text = "$prefix-${wall.format(DATE_ONLY_FORMAT)}-WA${"%04d".format(id)}"
                Stem(text, wall.toLocalDate().atStartOfDay(), false)
            }
            NameKind.EPOCH -> epochStem(wall)
            NameKind.NONE -> Stem("photo_$id", null, false)
            NameKind.IMPOSSIBLE -> Stem("IMG_20201301_120000", null, false)
            NameKind.NOT_MEDIA -> error("a non-media file is made in spec()")
        }
    }

    // An epoch name is an instant: it reads back as that instant on the machine, which in a clock change is not wall.
    private fun epochStem(wall: LocalDateTime): Stem {
        val millis = wall.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        return Stem(millis.toString(), time, false)
    }
}
