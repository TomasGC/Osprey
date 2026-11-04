package app.osprey.history

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

private val T0: Instant = Instant.parse("2026-11-03T10:00:00Z")

class RunSectionsTest {

    private fun run(id: Long, state: RunState) = RunRecord(id, "Action $id", state, T0)

    @Test
    fun `sections come in the order running, waiting, failed, stopped, interrupted, done`() {
        val runs = listOf(
            run(1, RunState.Done),
            run(2, RunState.Running),
            run(3, RunState.Failed),
            run(4, RunState.Interrupted),
            run(5, RunState.Queued),
            run(6, RunState.Stopped),
        )

        val sections = runSections(runs)

        assertEquals(
            listOf(
                RunState.Running,
                RunState.Queued,
                RunState.Failed,
                RunState.Stopped,
                RunState.Interrupted,
                RunState.Done,
            ),
            sections.map { it.state },
        )
        assertEquals(
            listOf("Running", "Waiting", "Failed", "Stopped", "Interrupted", "Done"),
            sections.map { it.title },
        )
    }

    @Test
    fun `a state with no run has no section`() {
        val sections = runSections(listOf(run(1, RunState.Done)))

        assertEquals(listOf(RunState.Done), sections.map { it.state })
    }

    @Test
    fun `the runs of a section keep the order they were given in`() {
        val sections = runSections(listOf(run(3, RunState.Failed), run(2, RunState.Done), run(1, RunState.Failed)))

        assertEquals(listOf(3L, 1L), sections.first().runs.map { it.id })
    }

    @Test
    fun `no run is no section`() {
        assertEquals(emptyList<RunSection>(), runSections(emptyList()))
    }
}
