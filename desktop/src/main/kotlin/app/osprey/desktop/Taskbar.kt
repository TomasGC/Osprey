package app.osprey.desktop

import app.osprey.jobs.TaskbarProgress
import app.osprey.jobs.TaskbarState
import java.awt.Taskbar
import java.awt.Window

/** The AWT state a [TaskbarState] stands for. */
fun awtStateOf(state: TaskbarState): Taskbar.State = when (state) {
    TaskbarState.NONE -> Taskbar.State.OFF
    TaskbarState.INDETERMINATE -> Taskbar.State.INDETERMINATE
    TaskbarState.NORMAL -> Taskbar.State.NORMAL
    TaskbarState.ERROR -> Taskbar.State.ERROR
}

/**
 * Shows [progress] on [window]'s taskbar button and, when [attention] is set, asks the taskbar to draw the user's
 * eye to it (Windows flashes the button). Does nothing on a platform without a taskbar or without a feature.
 */
fun updateTaskbar(window: Window, progress: TaskbarProgress, attention: Boolean) {
    if (!Taskbar.isTaskbarSupported()) return
    val taskbar = Taskbar.getTaskbar()
    if (taskbar.isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW)) {
        taskbar.setWindowProgressState(window, awtStateOf(progress.state))
    }
    val shows = progress.state == TaskbarState.NORMAL || progress.state == TaskbarState.ERROR
    if (shows && taskbar.isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)) {
        taskbar.setWindowProgressValue(window, progress.percent)
    }
    if (attention && taskbar.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
        taskbar.requestWindowUserAttention(window)
    }
}
