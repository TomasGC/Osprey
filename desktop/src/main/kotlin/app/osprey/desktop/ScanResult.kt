package app.osprey.desktop

/** One action tab's scan outcome: the cached plan plus what the pane displays. */
data class ScanResult<Plan>(
    val plan: Plan,
    val summary: String,
    val lines: List<String>,
    val count: Int,
)
