package app.osprey.desktop.media

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val RENDER_TIMEOUT_SECONDS = 60L
private const val PNG_COMPRESSION_LEVEL = "1"

/** The ffmpeg call that renders the first image of [path] as PNG on stdout; level 1 takes half the default's time. */
internal fun heicRenderCommand(path: Path): List<String> = listOf(
    "ffmpeg", "-nostdin", "-v", "error", "-i", path.toString(), "-frames:v", "1",
    "-f", "image2pipe", "-vcodec", "png", "-compression_level", PNG_COMPRESSION_LEVEL, "-",
)

/**
 * Real [HeicRenderer]: `ffmpeg` on PATH, the same binary video frames already need. Null when it is missing,
 * fails, produces nothing or runs past the timeout. The output is read on its own thread, so a hung ffmpeg
 * cannot block the caller past the timeout.
 */
object FfmpegHeicRenderer : HeicRenderer {

    @Suppress("SwallowedException") // ffmpeg missing or failing means "no image", never a crash.
    override fun renderPng(path: Path): ByteArray? = try {
        val process = ProcessBuilder(heicRenderCommand(path)).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = CompletableFuture.supplyAsync { process.inputStream.readBytes() }
        readOutput(process, output)
    } catch (e: IOException) {
        null
    }

    @Suppress("SwallowedException") // A timeout or a failed read ends the process and means "no image".
    private fun readOutput(process: Process, output: CompletableFuture<ByteArray>): ByteArray? = try {
        val bytes = output.get(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (process.waitFor() == 0 && bytes.isNotEmpty()) bytes else null
    } catch (e: TimeoutException) {
        process.destroyForcibly()
        null
    } catch (e: ExecutionException) {
        process.destroyForcibly()
        null
    }
}
