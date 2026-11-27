package app.osprey.desktop

private const val MAX_SCAN_WORKERS = 4

/**
 * How many files a Visual Dupes scan decodes at once: one per core, four at most. Past four a scan gains little, and
 * each decode adds an ffmpeg of some hundreds of megabytes (#87).
 */
fun scanWorkers(cores: Int = Runtime.getRuntime().availableProcessors()): Int = cores.coerceIn(1, MAX_SCAN_WORKERS)
