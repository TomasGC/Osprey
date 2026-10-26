package app.osprey.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.osprey.desktop.updateTaskbar
import app.osprey.jobs.JobQueue
import app.osprey.jobs.newlyFinished
import app.osprey.jobs.taskbarProgress
import java.awt.Window

/**
 * Keeps [window]'s taskbar button in step with [jobs]: progress while they run, red after a failure, and a call
 * for attention when one ends while the window is not the one the user is working in.
 */
@Composable
fun TaskbarEffect(jobs: JobQueue, window: Window) {
    val entries by jobs.entries.collectAsState()
    var seen by remember { mutableStateOf(entries) }
    LaunchedEffect(entries) {
        val ended = newlyFinished(seen, entries).isNotEmpty()
        seen = entries
        updateTaskbar(window, taskbarProgress(entries), attention = ended && !window.isFocused)
    }
}
