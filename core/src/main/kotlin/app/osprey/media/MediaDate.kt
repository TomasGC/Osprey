package app.osprey.media

import java.time.LocalDateTime

/**
 * A capture datetime recovered from a media file, and which [MediaDateSource] it came from.
 *
 * [dateTime] is zone-naive on purpose: EXIF and filenames never carry timezone info either (a
 * camera records "wall clock time at capture", not an instant), exactly like the POC's naive
 * `datetime.datetime`. A video container's `creation_time` is the exception: it is UTC, so a
 * reader converts it to local wall-clock time first ([utcToLocal]) to be comparable with the
 * others. Converting to an absolute instant only happens once, at the point a real
 * [app.osprey.filesystem.FileSystemRepository] actually writes a timestamp.
 */
data class MediaDate(val dateTime: LocalDateTime, val source: MediaDateSource)
