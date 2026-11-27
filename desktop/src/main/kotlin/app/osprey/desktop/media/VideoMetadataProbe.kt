package app.osprey.desktop.media

import app.osprey.media.utcToLocal
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import org.json.JSONObject

private const val FFPROBE_TIMEOUT_SECONDS = 30L
private val FFPROBE_TIMEOUT: Duration = Duration.ofSeconds(FFPROBE_TIMEOUT_SECONDS)

/**
 * `ffprobe`'s container `creation_time` for [path], converted from UTC to this machine's local
 * time, or null if `ffprobe` isn't on PATH, times out, fails, or the tag is absent/unparseable —
 * video actions degrade gracefully rather than crashing, same as the POC's `video_datetime`
 * (which, unlike this, read the UTC clock value as local time).
 */
fun probeVideoCreationTime(path: Path, timeout: Duration = FFPROBE_TIMEOUT): LocalDateTime? {
    val json = runFfprobe(path, timeout) ?: return null
    val raw = JSONObject(json)
        .optJSONObject("format")
        ?.optJSONObject("tags")
        ?.optString("creation_time")
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return try {
        utcToLocal(LocalDateTime.parse(raw.removeSuffix("Z")))
    } catch (e: DateTimeParseException) {
        null
    }
}

private fun runFfprobe(path: Path, timeout: Duration): String? = processOutput(
    listOf("ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", path.toString()),
    timeout,
)?.toString(Charsets.UTF_8)
