package app.osprey.desktop.media

import java.nio.file.Path
import java.time.Duration

private const val FFMPEG_TIMEOUT_SECONDS = 30L
private val FFMPEG_TIMEOUT: Duration = Duration.ofSeconds(FFMPEG_TIMEOUT_SECONDS)
private const val FRAME_EXTRACTION_OFFSET_SECONDS = "1"

/**
 * Extracts one representative frame from [path] as PNG bytes via `ffmpeg` (piped, no temp file),
 * matching the POC's `compute_video_phash`. Returns null if `ffmpeg` isn't on PATH, times out,
 * fails, or produces no output — video actions degrade gracefully rather than crashing.
 */
fun extractVideoFramePng(path: Path, timeout: Duration = FFMPEG_TIMEOUT): ByteArray? = processOutput(
    listOf(
        "ffmpeg", "-nostdin", "-ss", FRAME_EXTRACTION_OFFSET_SECONDS, "-i", path.toString(),
        "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-",
    ),
    timeout,
)
