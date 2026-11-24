package app.osprey.android.filesystem

import androidx.test.uiautomator.UiDevice

private const val STAGE_TRIES = 20
private const val STAGE_POLL_MS = 250L

/**
 * Folders and files a test puts on the device through the shell, and takes away again. The shell command is split at
 * spaces and runs without a shell, so each command goes through `sh -c` as one word, with a space in a path written
 * as `${IFS:0:1}`: a folder name with a space can be made, found and removed. A path is checked
 * after it is made, and made again if it is not there yet: on a volume that has just been mounted a file made right
 * after its folder can be lost, and a test must not take that for what it is testing.
 */
internal class StagedPaths(private val device: UiDevice) {
    private val staged = mutableListOf<String>()

    fun folder(path: String): String = make(path) { run("mkdir", "-p", path) }

    fun file(path: String): String = make(path) {
        run("mkdir", "-p", path.substringBeforeLast('/'))
        run("touch", path)
    }

    fun exists(path: String): Boolean = run("ls", "-d", path).trim() == path

    fun removeAll() {
        staged.forEach { run("rm", "-rf", it) }
        staged.clear()
    }

    private fun run(vararg words: String): String {
        val script = words.joinToString("\${IFS}") { "\"${it.replace(" ", "\${IFS:0:1}")}\"" }
        return device.executeShellCommand("sh -c $script")
    }

    private fun make(path: String, create: () -> Unit): String {
        repeat(STAGE_TRIES) {
            create()
            if (exists(path)) {
                staged += path
                return path
            }
            Thread.sleep(STAGE_POLL_MS)
        }
        error("could not stage $path")
    }
}
