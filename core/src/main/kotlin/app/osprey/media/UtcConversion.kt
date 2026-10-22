package app.osprey.media

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Converts [utc], a zone-naive reading of a UTC clock (a video container's `creation_time` is
 * always UTC), to the wall-clock time in [zone]. EXIF and filename dates are local wall-clock
 * time, so a container time has to be converted to be comparable with them.
 */
fun utcToLocal(utc: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
