package app.osprey.fixtures

import app.osprey.actions.heicExtensions
import java.nio.file.Files
import java.nio.file.Path
import org.junit.AssumptionViolatedException

private const val SIBLING = "Osprey-fixtures"
private const val REPOSITORY_MARKER = "settings.gradle.kts"
private const val HEIC_FOLDER = "heic"
private const val JPEG_FOLDER = "jpg"
private const val PNG_FOLDER = "png"
private const val REQUIRED = "1"

/**
 * Where the private real-photo fixtures are (#76). Osprey is public, so real photos live in `Osprey-fixtures`: found
 * through `FIXTURES_DIR`, else as the sibling of the repository root. A test that needs them calls [need]:
 * without the folder it is skipped, unless `REQUIRE_FIXTURES` is 1 (CI on the repository's own branches), where a
 * missing folder must fail instead of silently turning the tests off.
 */
object Photos {
    const val FOLDER_VARIABLE = "FIXTURES_DIR"
    const val REQUIRE_VARIABLE = "REQUIRE_FIXTURES"

    /** The fixtures folder, or null. A variable that names a missing folder means none: it never falls back. */
    fun locate(env: (String) -> String? = System::getenv, from: Path = Path.of("").toAbsolutePath()): Path? {
        val named = env(FOLDER_VARIABLE)
        if (named != null) return Path.of(named).takeIf { Files.isDirectory(it) }
        val root = generateSequence(from) { it.parent }.firstOrNull { Files.exists(it.resolve(REPOSITORY_MARKER)) }
        return root?.resolveSibling(SIBLING)?.takeIf { Files.isDirectory(it) }
    }

    /** The fixtures folder; where it is missing the test is skipped, or fails when [REQUIRE_VARIABLE] is 1. */
    fun need(env: (String) -> String? = System::getenv, from: Path = Path.of("").toAbsolutePath()): Path {
        val folder = locate(env, from)
        if (folder != null) return folder
        val reason = "The real-photo fixtures are missing: clone $SIBLING next to the repository or set $FOLDER_VARIABLE"
        if (env(REQUIRE_VARIABLE) == REQUIRED) throw AssertionError("$reason ($REQUIRE_VARIABLE is set)")
        throw AssumptionViolatedException(reason)
    }

    /** The JPEG photos of [folder], in name order. */
    fun jpeg(folder: Path): List<Path> = listing(folder, JPEG_FOLDER, setOf(".jpg", ".jpeg"))

    /** The PNG photos of [folder], in name order. */
    fun png(folder: Path): List<Path> = listing(folder, PNG_FOLDER, setOf(".png"))

    /** The HEIC photos of [folder], in name order. */
    fun heic(folder: Path): List<Path> = listing(folder, HEIC_FOLDER, heicExtensions)

    private fun listing(folder: Path, name: String, extensions: Set<String>): List<Path> {
        val directory = folder.resolve(name)
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { entries ->
            entries.filter { entry -> extensions.any { entry.fileName.toString().lowercase().endsWith(it) } }
                .sorted().toList()
        }
    }
}
