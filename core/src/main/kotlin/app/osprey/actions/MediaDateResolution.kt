package app.osprey.actions

import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.MediaMetadataReader
import java.time.LocalDateTime

private const val EARLIEST_CAPTURE_YEAR = 1970

// An unset camera clock leaves EXIF "0000:00:00 00:00:00" (year 0 once parsed) and an MP4 with no
// creation time reports its 1904 epoch: neither is a capture date, and no file written by a
// digital camera predates the Unix epoch.
private val EARLIEST_CAPTURE_DATE: LocalDateTime = LocalDateTime.of(EARLIEST_CAPTURE_YEAR, 1, 1, 0, 0)

/**
 * The best-known capture datetime for [name]/[entry]: EXIF/video-container metadata (via
 * [reader]) first, a date parsed from [name] otherwise. A reader date before 1970 is ignored
 * as an unset placeholder, so it falls through to the filename. Shared by the Dates (#6) and
 * Standardize (#7) actions, which each resolve it independently rather than one reading the
 * other's result — a POC invariant, not an accident of shared code.
 */
fun resolveMediaDate(entry: FileHandle, name: String, reader: MediaMetadataReader): MediaDate? {
    reader.captureDateTime(entry)?.takeIf { it.dateTime >= EARLIEST_CAPTURE_DATE }?.let { return it }
    val stem = splitStemAndSuffix(name).first
    val dateTime = filenameDateTime(stem) ?: return null
    return MediaDate(dateTime, MediaDateSource.FILENAME)
}

/** The POC's display strings for a [MediaDateSource], used in `SET`/`RENAME` log lines. */
fun displayName(source: MediaDateSource): String = when (source) {
    MediaDateSource.EXIF -> "EXIF"
    MediaDateSource.VIDEO_METADATA -> "VIDEO-META"
    MediaDateSource.FILENAME -> "FILENAME"
}
