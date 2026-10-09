package com.lucas.mobilemanager.domain.usage

import com.lucas.mobilemanager.domain.INSTAGRAM
import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.YOUTUBE
import com.lucas.mobilemanager.domain.usage.UsageEvent.Type.PAUSED
import com.lucas.mobilemanager.domain.usage.UsageEvent.Type.RESUMED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class ContinuousSessionTest {

    private val base = MONDAY.atTime(LocalTime.of(10, 0)).toInstant(java.time.ZoneOffset.UTC)
    private val from = base.minus(Duration.ofHours(12))
    private val gap = Duration.ofSeconds(60)

    private fun t(minutes: Long, seconds: Long = 0): Instant = base + Duration.ofMinutes(minutes) + Duration.ofSeconds(seconds)
    private fun ev(type: UsageEvent.Type, at: Instant, pkg: String = INSTAGRAM) = UsageEvent(pkg, type, at)

    private fun current(events: List<UsageEvent>, now: Instant, pkg: String = INSTAGRAM) =
        ContinuousSessions.current(events, pkg, from, now, gap)

    @Test
    fun `single open session counts from its start`() {
        val session = current(listOf(ev(RESUMED, t(0))), now = t(12))
        assertEquals(ContinuousSession(t(0), Duration.ofMinutes(12)), session)
    }

    @Test
    fun `short interruptions are merged`() {
        val events = listOf(
            ev(RESUMED, t(0)), ev(PAUSED, t(5)),
            ev(RESUMED, t(5, 20)), ev(PAUSED, t(9)),
            ev(RESUMED, t(9, 45)),
        )
        assertEquals(ContinuousSession(t(0), Duration.ofMinutes(12)), current(events, now = t(12)))
    }

    @Test
    fun `a long absence starts a new session`() {
        val events = listOf(ev(RESUMED, t(0)), ev(PAUSED, t(5)), ev(RESUMED, t(7)))
        assertEquals(ContinuousSession(t(7), Duration.ofMinutes(3)), current(events, now = t(10)))
    }

    @Test
    fun `interruption exactly equal to the gap starts a new session`() {
        val events = listOf(ev(RESUMED, t(0)), ev(PAUSED, t(5)), ev(RESUMED, t(6)))
        assertEquals(t(6), current(events, now = t(8))!!.start)
    }

    @Test
    fun `app not in foreground has no current session`() {
        val events = listOf(ev(RESUMED, t(0)), ev(PAUSED, t(5)))
        assertNull(current(events, now = t(5, 10)))
    }

    @Test
    fun `screen off pauses the app and ends the session after the gap`() {
        // Écran éteint à 5 min (pause), rallumé 3 min plus tard sur la même app.
        val events = listOf(ev(RESUMED, t(0)), ev(PAUSED, t(5)), ev(RESUMED, t(8)))
        assertEquals(Duration.ofMinutes(1), current(events, now = t(9))!!.length)
    }

    @Test
    fun `app already open before the window counts from the window start`() {
        val events = listOf(ev(PAUSED, t(-1)), ev(RESUMED, t(-1, 30)))
        assertEquals(from, current(events, now = t(0))!!.start)
    }

    @Test
    fun `unsorted events are handled`() {
        val events = listOf(ev(RESUMED, t(5, 30)), ev(PAUSED, t(5)), ev(RESUMED, t(0)))
        assertEquals(t(0), current(events, now = t(10))!!.start)
    }

    @Test
    fun `other packages do not interrupt nor extend the session`() {
        val events = listOf(
            ev(RESUMED, t(0), YOUTUBE), ev(PAUSED, t(3), YOUTUBE),
            ev(RESUMED, t(3)),
        )
        assertEquals(ContinuousSession(t(3), Duration.ofMinutes(2)), current(events, now = t(5)))
    }

    @Test
    fun `future events are ignored`() {
        val events = listOf(ev(RESUMED, t(0)), ev(PAUSED, t(20)))
        assertEquals(Duration.ofMinutes(10), current(events, now = t(10))!!.length)
    }
}
