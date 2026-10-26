package app.osprey.desktop

import app.osprey.jobs.TaskbarState
import java.awt.Taskbar
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskbarTest {

    @Test
    fun `every taskbar state has its AWT counterpart`() {
        assertEquals(Taskbar.State.OFF, awtStateOf(TaskbarState.NONE))
        assertEquals(Taskbar.State.INDETERMINATE, awtStateOf(TaskbarState.INDETERMINATE))
        assertEquals(Taskbar.State.NORMAL, awtStateOf(TaskbarState.NORMAL))
        assertEquals(Taskbar.State.ERROR, awtStateOf(TaskbarState.ERROR))
    }
}
