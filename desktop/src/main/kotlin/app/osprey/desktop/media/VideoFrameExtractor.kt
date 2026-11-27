package app.osprey.desktop.media

import java.nio.file.Path
import java.time.Duration

private const val FFMPEG_TIMEOUT_SECONDS = 30L
private val FFMPEG_TIMEOUT: Duration = Duration.ofSeconds(FFMPEG_TIMEOUT_SECONDS)
private const val FRAME_EXTRACTION_OFFSET_SECONDS = "1"
private const val DECODER_THREADS = "4"

/**
 * The ffmpeg call that writes the representative frame of the video at [path] on stdout, as PNG. The decoder gets
 * [DECODER_THREADS] threads: by default ffmpeg starts one per core, each holding frames, which for a 4K video was
 * some 780 MB per decode against some 300 MB with the limit, for the same frame (#87).
 */
fun videoFrameCommand(path: Path): List<String> = listOf(
    "ffmpeg", "-nostdin", "-threads", DECODER_THREADS, "-ss", FRAME_EXTRACTION_OFFSET_SECONDS, "-i", path.toString(),
    "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-",
)

/**
 * Extracts one representative frame from [path] as PNG bytes via `ffmpeg` (piped, no temp file),
 * matching the POC's `compute_video_phash`. Returns null if `ffmpeg` isn't on PATH, times out,
 * fails, or produces no output — video actions degrade gracefully rather than crashing.
 */
fun extractVideoFramePng(path: Path, timeout: Duration = FFMPEG_TIMEOUT): ByteArray? =
    processOutput(videoFrameCommand(path), timeout)
