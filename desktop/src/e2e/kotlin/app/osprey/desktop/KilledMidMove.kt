package app.osprey.desktop

import app.osprey.actions.reorg.ReorgPlanner
import app.osprey.actions.reorg.ReorgSource
import app.osprey.desktop.filesystem.DesktopFileSystemRepository
import app.osprey.desktop.filesystem.PathHandle
import app.osprey.desktop.media.DesktopMediaMetadataReader
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.settings.InputMode
import java.nio.file.Path

/** The real file system, except that the first delete says it is ready, then never returns: a Move stopped mid-run. */
private class BlocksOnFirstDelete(private val inner: FileSystemRepository) : FileSystemRepository by inner {

    override fun delete(entry: FileHandle) = blockAfterSaying(READY)
}

/**
 * A process for [ReorgKillEndToEndTest] to kill (#91): applies a Reorg Move of `args[1]` into the library `args[0]`
 * and stops inside the delete of the original, after its copy was written and verified, printing [READY] first.
 */
fun main(args: Array<String>) {
    val output = PathHandle(Path.of(args[0]))
    val sources = listOf(ReorgSource(PathHandle(Path.of(args[1])), "Camera", InputMode.MOVE))
    val planner = ReorgPlanner(BlocksOnFirstDelete(DesktopFileSystemRepository()), DesktopMediaMetadataReader())
    planner.apply(planner.plan(output, sources), output)
}
