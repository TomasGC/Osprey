package app.osprey.jobs

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPaceTest {

    private val interval = 500L

    @Test
    fun theFirstPostGoesAtOnce() {
        assertEquals(0L, notificationDelayMs(lastPostMs = null, nowMs = 1_000L, intervalMs = interval))
    }

    @Test
    fun aPostSoonAfterAnotherWaitsForTheRestOfTheInterval() {
        assertEquals(300L, notificationDelayMs(lastPostMs = 1_000L, nowMs = 1_200L, intervalMs = interval))
    }

    @Test
    fun aPostInTheSameInstantWaitsAWholeInterval() {
        assertEquals(interval, notificationDelayMs(lastPostMs = 1_000L, nowMs = 1_000L, intervalMs = interval))
    }

    @Test
    fun aPostExactlyOneIntervalLaterGoesAtOnce() {
        assertEquals(0L, notificationDelayMs(lastPostMs = 1_000L, nowMs = 1_500L, intervalMs = interval))
    }

    @Test
    fun aPostLongAfterAnotherGoesAtOnce() {
        assertEquals(0L, notificationDelayMs(lastPostMs = 1_000L, nowMs = 60_000L, intervalMs = interval))
    }

    @Test
    fun aClockThatWentBackwardsWaitsAtMostOneInterval() {
        assertEquals(interval, notificationDelayMs(lastPostMs = 5_000L, nowMs = 1_000L, intervalMs = interval))
    }

    @Test
    fun theDefaultIntervalIsHalfASecond() {
        assertEquals(500L, NOTIFICATION_INTERVAL_MS)
        assertEquals(500L, notificationDelayMs(lastPostMs = 1_000L, nowMs = 1_000L))
    }

    /** What the service does: wait the delay, then post whatever is latest. A burst posts its first and its last. */
    @Test
    fun aBurstPostsTheFirstStateAndTheLastOne() {
        val posts = mutableListOf<Pair<Long, Int>>()
        var last: Long? = null
        var clock = 0L
        var latest = 0
        for (state in 1..30) {
            latest = state
            val wait = notificationDelayMs(last, clock, interval)
            if (wait == 0L) {
                posts += clock to latest
                last = clock
            }
            clock += 10L
        }
        clock = last!! + interval
        posts += clock to latest

        assertEquals(listOf(0L to 1, 500L to 30), posts)
    }
}
