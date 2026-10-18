package app.osprey.media

import java.time.Instant

/** A capture datetime recovered from a media file, and which [MediaDateSource] it came from. */
data class MediaDate(val dateTime: Instant, val source: MediaDateSource)
