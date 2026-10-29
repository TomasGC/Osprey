package app.osprey.viewer

/** Bytes of heap a save could still take, after asking for a collection so garbage is not counted as used. */
fun freeHeapBytes(): Long {
    val runtime = Runtime.getRuntime()
    runtime.gc()
    return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
}
