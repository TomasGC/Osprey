package app.osprey.actions

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Ported from the POC's `_common.py` FILENAME_PATTERNS verbatim. The POC also registers the
// WA pattern here, but guards it with a `len(m.groups()) == 6` check that it can never pass
// (it only has 3 groups) -- it's a dead branch there. Modeled explicitly below as
// dateOnlyPatterns instead, same net behavior, without the dead check.
private val fullDatetimePatterns = listOf(
    Regex("""(?:IMG|VID|MOV)_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})"""),
    Regex("""Ultimate_HDR_Camera_Original_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})"""),
    Regex("""AR_EFFECT_(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})"""),
    Regex("""download_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})"""),
    Regex("""^(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})"""),
    Regex("""(?:Screenshot|SmartSelect)_(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})"""),
    Regex("""CameraZOOM-(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})"""),
    // What Standardize (#7) names a file: IMG_ or VID_, then yyyy-MM-dd_HH-mm-ss, with a collision suffix possibly
    // after it. Read back like a camera's own name, so a file with no metadata keeps its date and a video its time
    // after the first run (#84).
    Regex("""^(?:IMG|VID)_(\d{4})-(\d{2})-(\d{2})_(\d{2})-(\d{2})-(\d{2})"""),
)

// WhatsApp's IMG-YYYYMMDD-WA#### naming carries no time of day.
private val dateOnlyPatterns = listOf(
    Regex("""(?:IMG|VID)-(\d{4})(\d{2})(\d{2})-WA\d+"""),
)

private val epochMillisPattern = Regex("""^\d{13}$""")

private fun fullDatetimeMatch(stem: String): MatchResult? = fullDatetimePatterns.firstNotNullOfOrNull { it.find(stem) }

/**
 * The wall-clock time a camera wrote into [stem] (#84), or Standardize wrote back from it: only the patterns that
 * carry a time of day. A date-only name reads as midnight and an epoch-millis name is an instant shown in the
 * machine's zone, so neither says what the clock at the place of shooting showed. Null when no such pattern matches
 * or its date is impossible.
 */
fun filenameWallClock(stem: String): LocalDateTime? =
    fullDatetimeMatch(stem)?.let { dateTimeFromGroups(it.groupValues) }

/**
 * Parses a capture datetime out of [stem] (a filename without its extension), trying each known
 * camera/sync-app naming convention in turn, then a bare 13-digit epoch-millis timestamp.
 * Returns null if nothing matches, or if the first pattern that matches encodes an impossible
 * date (e.g. month 13) — ported from the POC's `filename_datetime` exactly, pattern order
 * included: once a pattern matches, its result (valid or not) is final, no other pattern is tried.
 */
fun filenameDateTime(stem: String): LocalDateTime? {
    fullDatetimeMatch(stem)?.let { return dateTimeFromGroups(it.groupValues) }
    for (pattern in dateOnlyPatterns) {
        val match = pattern.find(stem) ?: continue
        return dateOnlyFromGroups(match.groupValues)
    }
    if (epochMillisPattern.matches(stem)) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(stem.toLong()), ZoneId.systemDefault())
    }
    return null
}

// Regex group indices (year..second), not business constants -- not worth naming.
@Suppress("MagicNumber", "SwallowedException")
private fun dateTimeFromGroups(groups: List<String>): LocalDateTime? = try {
    LocalDateTime.of(
        groups[1].toInt(), groups[2].toInt(), groups[3].toInt(),
        groups[4].toInt(), groups[5].toInt(), groups[6].toInt(),
    )
} catch (e: DateTimeException) {
    null
}

@Suppress("MagicNumber", "SwallowedException")
private fun dateOnlyFromGroups(groups: List<String>): LocalDateTime? = try {
    LocalDateTime.of(groups[1].toInt(), groups[2].toInt(), groups[3].toInt(), 0, 0, 0)
} catch (e: DateTimeException) {
    null
}
