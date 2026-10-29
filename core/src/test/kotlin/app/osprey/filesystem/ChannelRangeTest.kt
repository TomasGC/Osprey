package app.osprey.filesystem

import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A file of six bytes behind the two positional calls a channel offers. */
class ChannelRangeTest {

    private val disk = byteArrayOf(1, 2, 3, 4, 5, 6)
    private val size = disk.size.toLong()

    /** A read that hands out at most [perCall] bytes at a time and answers -1 at the end, like a channel. */
    private fun readAt(perCall: Int = Int.MAX_VALUE): (ByteBuffer, Long) -> Int = { buffer, at ->
        val count = minOf(buffer.remaining(), perCall, (disk.size - at).toInt())
        if (count <= 0) {
            -1
        } else {
            buffer.put(disk, at.toInt(), count)
            count
        }
    }

    private fun writeAt(perCall: Int = Int.MAX_VALUE): (ByteBuffer, Long) -> Int = { buffer, at ->
        val count = minOf(buffer.remaining(), perCall)
        buffer.get(disk, at.toInt(), count)
        count
    }

    @Test
    fun `read returns the bytes from the offset`() =
        assertArrayEquals(byteArrayOf(3, 4, 5), ChannelRange.read(size, 2L, 3, readAt()))

    @Test
    fun `read is shorter at the end of the file and empty past it`() {
        assertArrayEquals(byteArrayOf(5, 6), ChannelRange.read(size, 4L, 100, readAt()))
        assertArrayEquals(byteArrayOf(), ChannelRange.read(size, 10L, 5, readAt()))
        assertArrayEquals(byteArrayOf(), ChannelRange.read(size, 0L, 0, readAt()))
    }

    @Test
    fun `read goes on until the range is full when the channel gives little at a time`() =
        assertArrayEquals(byteArrayOf(2, 3, 4, 5), ChannelRange.read(size, 1L, 4, readAt(perCall = 1)))

    @Test
    fun `read fails when the file ends before its size said`() {
        val shrunk = { _: ByteBuffer, _: Long -> -1 }
        assertThrows(IOException::class.java) { ChannelRange.read(size, 0L, 3, shrunk) }
    }

    @Test
    fun `write replaces the range and nothing else`() {
        ChannelRange.write(size, 1L, byteArrayOf(9, 8), writeAt())
        assertArrayEquals(byteArrayOf(1, 9, 8, 4, 5, 6), disk)
    }

    @Test
    fun `write goes on until everything is written when the channel takes little at a time`() {
        ChannelRange.write(size, 2L, byteArrayOf(7, 8, 9), writeAt(perCall = 1))
        assertArrayEquals(byteArrayOf(1, 2, 7, 8, 9, 6), disk)
    }

    @Test
    fun `write may end exactly at the end of the file`() {
        ChannelRange.write(size, 4L, byteArrayOf(8, 9), writeAt())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 8, 9), disk)
    }

    @Test
    fun `write outside the file throws and writes nothing`() {
        val untouched = disk.copyOf()
        for (offset in listOf(5L, 6L, -1L)) {
            assertThrows(IOException::class.java) { ChannelRange.write(size, offset, byteArrayOf(9, 9), writeAt()) }
        }
        assertArrayEquals(untouched, disk)
    }

    @Test
    fun `write fails when the channel makes no progress`() {
        val stuck = { _: ByteBuffer, _: Long -> 0 }
        assertThrows(IOException::class.java) { ChannelRange.write(size, 0L, byteArrayOf(9), stuck) }
        assertEquals(1, disk[0].toInt())
    }
    /** A file that starts empty and grows as it is written, behind the positional write a channel offers. */
    private fun growingWriteAt(into: ByteArray, positions: MutableList<Long>, perCall: Int = Int.MAX_VALUE) =
        { buffer: ByteBuffer, at: Long ->
            val count = minOf(buffer.remaining(), perCall)
            positions += at
            buffer.get(into, at.toInt(), count)
            count
        }

    @Test
    fun `writeAll writes every byte from the start whatever the size of the file`() {
        val into = ByteArray(5)
        ChannelRange.writeAll(byteArrayOf(1, 2, 3, 4, 5), growingWriteAt(into, mutableListOf()))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), into)
    }

    @Test
    fun `writeAll goes on from where the channel stopped`() {
        val into = ByteArray(4)
        val positions = mutableListOf<Long>()
        ChannelRange.writeAll(byteArrayOf(1, 2, 3, 4), growingWriteAt(into, positions, perCall = 1))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), into)
        assertEquals(listOf(0L, 1L, 2L, 3L), positions)
    }

    @Test
    fun `writeAll of nothing asks the channel for nothing`() {
        val positions = mutableListOf<Long>()
        ChannelRange.writeAll(byteArrayOf(), growingWriteAt(ByteArray(1), positions))
        assertEquals(emptyList<Long>(), positions)
    }

    @Test
    fun `writeAll fails when the channel makes no progress`() {
        assertThrows(IOException::class.java) { ChannelRange.writeAll(byteArrayOf(9)) { _, _ -> 0 } }
    }
}
