package app.osprey.desktop.media

import app.osprey.media.HeicRotation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

private const val RENDER_TIMEOUT_SECONDS = 60L
private val RENDER_TIMEOUT: Duration = Duration.ofSeconds(RENDER_TIMEOUT_SECONDS)
private const val PNG_COMPRESSION_LEVEL = "1"

/** The ffmpeg call that renders the first image of [path] as PNG on stdout; level 1 takes half the default's time. */
fun heicRenderCommand(path: Path): List<String> = listOf(
    "ffmpeg", "-nostdin", "-v", "error", "-i", path.toString(), "-frames:v", "1",
    "-f", "image2pipe", "-vcodec", "png", "-compression_level", PNG_COMPRESSION_LEVEL, "-",
)

/** The ffprobe call that lists the stream groups of [path], where a phone HEIC keeps its tile grid, as JSON. */
internal fun heicProbeCommand(path: Path): List<String> =
    listOf("ffprobe", "-v", "error", "-show_stream_groups", "-of", "json", path.toString())

fun runFfmpeg(command: List<String>): ByteArray? = processOutput(command, RENDER_TIMEOUT)

fun probeHeicGroups(path: Path): String? = runFfmpeg(heicProbeCommand(path))?.toString(Charsets.UTF_8)

@Suppress("SwallowedException") // a photo that cannot be read is shown as stored
private fun heicQuarterTurns(path: Path): Int = try {
    HeicRotation.of(Files.readAllBytes(path))
} catch (e: IOException) {
    0
}

/**
 * Real [HeicRenderer]: `ffmpeg` and `ffprobe` on PATH, the same binaries video frames already need. A phone HEIC is
 * a grid of tiles, which an ffmpeg before 8.1 decodes as its first tile only: [GridHeicRenderer] composes it. Null when
 * a binary is missing, fails, produces nothing or runs past the timeout.
 */
object FfmpegHeicRenderer : HeicRenderer by GridHeicRenderer(::probeHeicGroups, ::runFfmpeg, ::heicQuarterTurns)
