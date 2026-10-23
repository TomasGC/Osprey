package app.osprey.filesystem

import java.util.Locale

private const val UNIT = 1024L

/** A file size for display: whole bytes or kilobytes, then megabytes and gigabytes with one decimal. */
fun formatSize(bytes: Long): String = when {
    bytes < UNIT -> "$bytes B"
    bytes < UNIT * UNIT -> "${bytes / UNIT} KB"
    bytes < UNIT * UNIT * UNIT -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / (UNIT * UNIT))
    else -> String.format(Locale.ROOT, "%.1f GB", bytes.toDouble() / (UNIT * UNIT * UNIT))
}
