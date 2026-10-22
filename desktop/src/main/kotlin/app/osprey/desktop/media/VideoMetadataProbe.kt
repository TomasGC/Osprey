package app.osprey.desktop.media

import app.osprey.media.utcToLocal
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import org.json.JSONObject

private const val FFPROBE_TIMEOUT_SECONDS = 30L

/**
 * `ffprobe`'s container `creation_time` for [path], converted from UTC to this machine's local
 * time, or null if `ffprobe` isn't on PATH, times out, fails, or the tag is absent/unparseable —
 * video actions degrade gracefully rather than crashing, same as the POC's `video_datetime`
 * (which, unlike this, read the UTC clock value as local time).
 */
fun probeVideoCreationTime(path: Path): LocalDateTime? {
    val json = runFfprobe(path) ?: return null
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

@Suppress("SwallowedException") // ffprobe missing/failing returns null -- graceful degradation, not a crash.
private fun runFfprobe(path: Path): String? = try {
    val process = ProcessBuilder(
        "ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", path.toString(),
    ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val output = process.inputStream.bufferedReader().readText()
    val finished = process.waitFor(FFPROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!finished) process.destroyForcibly()
    if (finished && process.exitValue() == 0) output else null
} catch (e: IOException) {
    null
}
