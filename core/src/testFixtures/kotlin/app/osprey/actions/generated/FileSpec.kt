package app.osprey.actions.generated

import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.utcToLocal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** What a generated file's metadata says: what the media reader would return for it. */
sealed interface SpecMetadata {
    /** Nothing readable: only the file's name can date it. */
    data object None : SpecMetadata

    /** A photo's EXIF capture time: local wall-clock time, no zone. */
    data class Exif(val local: LocalDateTime) : SpecMetadata

    /** A video container holding [utc], shot where the clock was [offsetSeconds] ahead of UTC. */
    data class Container(val utc: LocalDateTime, val offsetSeconds: Int) : SpecMetadata

    /** A container whose creation time was never set: the 1904 epoch. */
    data object UnsetContainer : SpecMetadata
}

private val UNSET_CONTAINER_TIME = LocalDateTime.of(1904, 1, 1, 0, 0)

/** What the reader reports for this metadata on this machine: a container's UTC instant is shown in its zone. */
fun SpecMetadata.asMediaDate(): MediaDate? = when (this) {
    SpecMetadata.None -> null
    is SpecMetadata.Exif -> MediaDate(local, MediaDateSource.EXIF)
    is SpecMetadata.Container -> MediaDate(utcToLocal(utc), MediaDateSource.VIDEO_METADATA)
    SpecMetadata.UnsetContainer -> MediaDate(UNSET_CONTAINER_TIME, MediaDateSource.VIDEO_METADATA)
}

/** The machine's offset from UTC, in seconds, at the UTC instant [utc]. */
fun machineOffsetSeconds(utc: LocalDateTime): Int =
    ZoneId.systemDefault().rules.getOffset(utc.toInstant(ZoneOffset.UTC)).totalSeconds

/** The date a file must resolve to, where one answer is clear without the rules that choose between sources (#136). */
sealed interface ExpectedDate {
    /** No metadata and no date in the name: nothing to date it by. */
    data object Undated : ExpectedDate

    data class At(val time: LocalDateTime) : ExpectedDate

    /** A video shot at another offset, whose name holds a time: #84's rule chooses, and its own tests cover it. */
    data object Unclear : ExpectedDate
}

/**
 * One generated file: a unique [id] (its content too), its [name], and what its [metadata] says. [nameTime] is
 * what the name reads as, null when it holds no date: a date-only name reads as midnight and an epoch name as an
 * instant on the machine. [wallClockName] says the name holds the clock of the place of shooting.
 */
data class FileSpec(
    val id: Int,
    val name: String,
    val metadata: SpecMetadata,
    val nameTime: LocalDateTime? = null,
    val wallClockName: Boolean = false,
) {
    val content: ByteArray get() = "spec-$id".toByteArray()

    fun describe(): String = "#$id $name  $metadata"

    /**
     * What a planner must resolve this file to. EXIF is read first, whatever the name says. A container is shown on
     * the machine; it wins when it is at the machine's own offset (the name agrees) or the name holds no clock time.
     * With neither, the name's date is used, or the file is undated.
     */
    fun expectedDate(): ExpectedDate = when (metadata) {
        is SpecMetadata.Exif -> ExpectedDate.At(metadata.local)
        is SpecMetadata.Container -> when {
            metadata.offsetSeconds == machineOffsetSeconds(metadata.utc) || !wallClockName ->
                ExpectedDate.At(utcToLocal(metadata.utc))
            else -> ExpectedDate.Unclear
        }
        SpecMetadata.None, SpecMetadata.UnsetContainer -> nameTime?.let { ExpectedDate.At(it) } ?: ExpectedDate.Undated
    }
}
