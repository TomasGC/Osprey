package app.osprey.desktop.testing

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

// What the tests about names that differ only by case (#132) share, in every desktop tier.

/** Whether [folder] takes two names that differ only by case for one: true on Windows, false on Linux. */
fun ignoresCase(folder: Path): Boolean {
    val probe = Files.writeString(folder.resolve("case-probe"), "")
    return Files.exists(folder.resolve("CASE-PROBE")).also { Files.delete(probe) }
}

/** Every file under [root] as `path=content`: a lost, overwritten or oddly named file shows. */
fun fileTree(root: Path): Set<String> = Files.walk(root).use { paths ->
    paths.filter { it.isRegularFile() }
        .map { "${root.relativize(it).joinToString("/")}=${Files.readString(it)}" }
        .toList().toSet()
}
