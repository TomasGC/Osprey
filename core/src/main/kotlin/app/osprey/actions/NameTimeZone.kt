package app.osprey.actions

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private const val SECONDS_PER_HOUR = 3600L
private const val TOLERANCE_SECONDS = 120L
private const val OFFSET_STEP_SECONDS = 900L
private const val MIN_OFFSET_SECONDS = -12 * SECONDS_PER_HOUR
private const val MAX_OFFSET_SECONDS = 14 * SECONDS_PER_HOUR

/**
 * True when a video's [name] time is its container's instant read at another UTC offset (#84): the camera wrote
 * the local time of the place of shooting in the name, while the container holds UTC, which the reader showed in
 * the machine's [zone].
 *
 * [container] is that machine-zone reading. The instant is rebuilt from it, and [name] must differ from the UTC
 * reading by a real offset: a multiple of 15 minutes (every zone, half-hour and 45-minute ones included) from
 * -12:00 to +14:00, within two minutes. A name that already agrees with [container] within the same tolerance
 * says nothing new, so it is not "another offset".
 */
fun isContainerAtOtherOffset(container: LocalDateTime, name: LocalDateTime, zone: ZoneId): Boolean {
    if (Duration.between(container, name).abs().seconds <= TOLERANCE_SECONDS) return false
    val utc = container.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    val offset = Duration.between(utc, name).seconds
    val nearest = Math.floorDiv(offset + OFFSET_STEP_SECONDS / 2, OFFSET_STEP_SECONDS) * OFFSET_STEP_SECONDS
    return Math.abs(offset - nearest) <= TOLERANCE_SECONDS && nearest in MIN_OFFSET_SECONDS..MAX_OFFSET_SECONDS
}
