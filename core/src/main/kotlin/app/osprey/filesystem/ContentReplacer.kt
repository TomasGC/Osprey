package app.osprey.filesystem

/** Replaces the whole content of a file, which a byte range cannot do once the size changes (#70). */
interface ContentReplacer {

    /**
     * Replaces the whole content of [entry] with [bytes], whatever the size. The handle stays valid. When this
     * throws, the file still holds what it held before, or the failure says where a copy of it was kept.
     */
    fun replaceContent(entry: FileHandle, bytes: ByteArray)
}
