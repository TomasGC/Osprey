package app.osprey.desktop.media

import java.nio.file.Path

/**
 * A [HeicRenderer] that composes the tile grid of a photo itself when it has one, so that an ffmpeg which decodes only
 * the first tile still shows the whole picture. A photo with no grid, or whose composition fails, goes to the plain
 * command, as before. [probe] answers ffprobe's stream groups, [run] runs an ffmpeg command and [turns] gives the
 * photo's `irot`: seams, so that each decision can be tested without a binary.
 */
class GridHeicRenderer(
    private val probe: (Path) -> String?,
    private val run: (List<String>) -> ByteArray?,
    private val turns: (Path) -> Int,
) : HeicRenderer {

    override fun renderPng(path: Path): ByteArray? {
        val grid = probe(path)?.let(::parseHeicGrid)
        val composed = grid?.let { run(heicGridCommand(path, it, turns(path))) }
        return composed ?: run(heicRenderCommand(path))
    }
}
