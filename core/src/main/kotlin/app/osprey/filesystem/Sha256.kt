package app.osprey.filesystem

import java.io.InputStream
import java.security.MessageDigest

private const val CHUNK_BYTES = 1 shl 20

/** SHA-256 of everything [input] yields, as lowercase hex, read in chunks so a large file is never fully in memory. */
fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(CHUNK_BYTES)
    var read = input.read(buffer)
    while (read >= 0) {
        digest.update(buffer, 0, read)
        read = input.read(buffer)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
