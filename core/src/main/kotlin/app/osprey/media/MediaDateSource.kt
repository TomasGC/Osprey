package app.osprey.media

/**
 * Where a [MediaDate] came from, in the Dates action's priority order (highest confidence
 * first): image EXIF, then video-container metadata, then the filename. [MediaMetadataReader]
 * only ever reports [EXIF] or [VIDEO_METADATA] — [FILENAME] is reserved for the Dates planner's
 * own fallback once a [MediaMetadataReader] turns up nothing.
 */
enum class MediaDateSource { EXIF, VIDEO_METADATA, FILENAME }
