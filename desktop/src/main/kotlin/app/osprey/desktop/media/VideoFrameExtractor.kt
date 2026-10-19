package app.osprey.desktop.media

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val FFMPEG_TIMEOUT_SECONDS = 30L
private const val FRAME_EXTRACTION_OFFSET_SECONDS = "1"

/**
 * Extracts one representative frame from [path] as PNG bytes via `ffmpeg` (piped, no temp file),
 * matching the POC's `compute_video_phash`. Returns null if `ffmpeg` isn't on PATH, times out,
 * fails, or produces no output — video actions degrade gracefully rather than crashing.
 */
@Suppress("SwallowedException") // ffmpeg missing/failing returns null -- graceful degradation, not a crash.
fun extractVideoFramePng(path: Path): ByteArray? = try {
    val process = ProcessBuilder(
        "ffmpeg",
        "-ss", FRAME_EXTRACTION_OFFSET_SECONDS,
        "-i", path.toString(),
        "-frames:v", "1",
        "-f", "image2pipe",
        "-vcodec", "png",
        "-",
    ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val output = process.inputStream.readBytes()
    val finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!finished) process.destroyForcibly()
    if (finished && process.exitValue() == 0 && output.isNotEmpty()) output else null
} catch (e: IOException) {
    null
}
