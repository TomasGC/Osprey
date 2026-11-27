package app.osprey.desktop.media

import app.osprey.media.HeicRotation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

private const val RENDER_TIMEOUT_SECONDS = 60L
private val RENDER_TIMEOUT: Duration = Duration.ofSeconds(RENDER_TIMEOUT_SECONDS)

/** The ffmpeg call that renders the first image of [path] on stdout, in the format of [output]. */
fun heicRenderCommand(path: Path, output: HeicOutput): List<String> =
    listOf("ffmpeg", "-nostdin", "-v", "error", "-i", path.toString(), "-frames:v", "1", "-f", "image2pipe") +
        output.arguments + "-"

/**
 * The ffprobe call that lists, as JSON, the streams of [path] and their groups: a phone HEIC keeps its tile grid in a
 * group, and each stream says how deep its pixels are.
 */
internal fun heicProbeCommand(path: Path): List<String> =
    listOf("ffprobe", "-v", "error", "-show_stream_groups", "-show_streams", "-of", "json", path.toString())

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
