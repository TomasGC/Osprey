package app.osprey.desktop.filesystem

import app.osprey.desktop.testing.TimedTest
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * On Windows a reader that opened the photo without sharing delete (ffmpeg decoding it for the viewer) makes the final
 * move over it fail with an access error until the reader lets go, a fraction of a second later (#93).
 */
class DesktopContentReplacerTest : TimedTest() {

    private lateinit var folder: Path
    private lateinit var photo: Path
    private val pauses = mutableListOf<Long>()
    private var moves = 0

    @Before
    fun setUp() {
        folder = Files.createTempDirectory("osprey-replacer-test")
        photo = folder.resolve("photo.heic")
        Files.write(photo, byteArrayOf(1, 2, 3))
    }

    @After
    fun tearDown() {
        folder.toFile().deleteRecursively()
    }

    private fun replacerDeniedFor(deniedMoves: Int, failure: () -> IOException = { AccessDeniedException("busy") }) =
        DesktopContentReplacer(
            move = { from, to ->
                moves++
                if (moves <= deniedMoves) throw failure()
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            },
            pause = { pauses += it },
        )

    private fun namesInFolder() = Files.list(folder).use { it.map { path -> path.fileName.toString() }.toList() }

    @Test
    fun `a move refused twice by a reader is retried and the content is replaced`() {
        replacerDeniedFor(deniedMoves = 2).replaceContent(PathHandle(photo), byteArrayOf(9, 8))

        assertArrayEquals(byteArrayOf(9, 8), Files.readAllBytes(photo))
        assertEquals(3, moves)
        assertEquals(listOf(100L, 100L), pauses)
        assertEquals(listOf("photo.heic"), namesInFolder())
    }

    @Test
    fun `a move refused for good fails after waiting five seconds and leaves the file and the folder as they were`() {
        val replacer = replacerDeniedFor(deniedMoves = Int.MAX_VALUE)

        assertThrows(AccessDeniedException::class.java) { replacer.replaceContent(PathHandle(photo), byteArrayOf(9, 8)) }

        assertEquals(5_000L, pauses.sum())
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(photo))
        assertEquals(listOf("photo.heic"), namesInFolder())
    }

    @Test
    fun `any other failure of the move is not retried`() {
        val replacer = replacerDeniedFor(deniedMoves = Int.MAX_VALUE) { IOException("disk gone") }

        assertThrows(IOException::class.java) { replacer.replaceContent(PathHandle(photo), byteArrayOf(9, 8)) }

        assertEquals(1, moves)
        assertEquals(emptyList<Long>(), pauses)
        assertEquals(listOf("photo.heic"), namesInFolder())
    }
}
