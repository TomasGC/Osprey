package app.osprey.desktop.media

import app.osprey.media.HeicRotation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val RENDER_TIMEOUT_SECONDS = 60L
private const val PNG_COMPRESSION_LEVEL = "1"

/** The ffmpeg call that renders the first image of [path] as PNG on stdout; level 1 takes half the default's time. */
fun heicRenderCommand(path: Path): List<String> = listOf(
    "ffmpeg", "-nostdin", "-v", "error", "-i", path.toString(), "-frames:v", "1",
    "-f", "image2pipe", "-vcodec", "png", "-compression_level", PNG_COMPRESSION_LEVEL, "-",
)

/** The ffprobe call that lists the stream groups of [path], where a phone HEIC keeps its tile grid, as JSON. */
internal fun heicProbeCommand(path: Path): List<String> =
    listOf("ffprobe", "-v", "error", "-show_stream_groups", "-of", "json", path.toString())

/** Stdout of [command] when it succeeds in time, else null. Read on its own thread, so a hung process cannot block. */
@Suppress("SwallowedException") // a missing binary, a failure or a timeout all mean "nothing", never a crash
private fun outputOf(command: List<String>): ByteArray? = try {
    val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val output = CompletableFuture.supplyAsync { process.inputStream.readBytes() }
    try {
        val bytes = output.get(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (process.waitFor() == 0 && bytes.isNotEmpty()) bytes else null
    } catch (e: TimeoutException) {
        process.destroyForcibly()
        null
    } catch (e: ExecutionException) {
        process.destroyForcibly()
        null
    }
} catch (e: IOException) {
    null
}

fun runFfmpeg(command: List<String>): ByteArray? = outputOf(command)

fun probeHeicGroups(path: Path): String? = outputOf(heicProbeCommand(path))?.toString(Charsets.UTF_8)

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
