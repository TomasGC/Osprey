package app.osprey.actions.dedupe

import java.security.MessageDigest

/**
 * MD5 content hash of [bytes], as a lowercase hex string — matches the POC exactly. This is for
 * duplicate *detection*, not security, so no stronger/slower algorithm is used or needed.
 */
fun fileHash(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("MD5").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}
