package app.osprey.actions.generated

import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.utcToLocal
import java.time.LocalDateTime

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

/** One generated file: a unique [id] (its content too), its [name], and what its [metadata] says. */
data class FileSpec(val id: Int, val name: String, val metadata: SpecMetadata) {
    val content: ByteArray get() = "spec-$id".toByteArray()

    fun describe(): String = "#$id $name  $metadata"
}
