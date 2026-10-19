package app.osprey.actions

import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaDate
import app.osprey.media.MediaDateSource
import app.osprey.media.MediaMetadataReader

/**
 * The best-known capture datetime for [name]/[entry]: EXIF/video-container metadata (via
 * [reader]) first, a date parsed from [name] otherwise. Shared by the Dates (#6) and
 * Standardize (#7) actions, which each resolve it independently rather than one reading the
 * other's result — a POC invariant, not an accident of shared code.
 */
fun resolveMediaDate(entry: FileHandle, name: String, reader: MediaMetadataReader): MediaDate? {
    reader.captureDateTime(entry)?.let { return it }
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
