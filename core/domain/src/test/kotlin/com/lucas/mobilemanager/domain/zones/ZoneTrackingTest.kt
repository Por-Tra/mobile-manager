package com.lucas.mobilemanager.domain.zones

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ZoneTrackingTest {

    private val t0 = Instant.parse("2026-10-12T10:00:00Z")
    private fun ms(m: Long) = t0.plusMillis(m)

    @Nested
    inner class Throttle {
        private val throttle = ZoneExitThrottle()

        @Test
        fun `first detection exits`() = assertEquals(ZoneExitThrottle.Action.EXIT, throttle.onZoneDetected(ms(0)))

        @Test
        fun `bursts within 700 ms wait`() {
            throttle.onZoneDetected(ms(0))
            assertEquals(ZoneExitThrottle.Action.WAIT, throttle.onZoneDetected(ms(300)))
            assertEquals(ZoneExitThrottle.Action.EXIT, throttle.onZoneDetected(ms(700)))
        }

        @Test
        fun `zone that persists after three exits escalates to the block screen`() {
            listOf(0L, 800L, 1600L).forEach { assertEquals(ZoneExitThrottle.Action.EXIT, throttle.onZoneDetected(ms(it))) }
            assertEquals(ZoneExitThrottle.Action.ESCALATE, throttle.onZoneDetected(ms(2400)))
        }

        @Test
        fun `leaving the zone resets the attempts`() {
            listOf(0L, 800L, 1600L).forEach { throttle.onZoneDetected(ms(it)) }
            throttle.onZoneLeft()
            assertEquals(ZoneExitThrottle.Action.EXIT, throttle.onZoneDetected(ms(1700)))
        }
    }

    @Nested
    inner class Visits {
        private val tracker = ZoneVisitTracker()

        @Test
        fun `visit is reported when leaving the zone`() {
            assertNull(tracker.onSnapshot("yt", "shorts", ms(0)))
            assertNull(tracker.onSnapshot("yt", "shorts", ms(30_000)))
            assertEquals(ZoneVisit("yt", "shorts", Duration.ofMinutes(1)), tracker.onSnapshot("yt", null, ms(60_000)))
        }

        @Test
        fun `very short visits are ignored`() {
            tracker.onSnapshot("yt", "shorts", ms(0))
            assertNull(tracker.onSnapshot("yt", null, ms(2_000)))
        }

        @Test
        fun `leaving the app ends the visit`() {
            tracker.onSnapshot("ig", "reels", ms(0))
            assertEquals(ZoneVisit("ig", "reels", Duration.ofSeconds(20)), tracker.onAppLeft(ms(20_000)))
            assertNull(tracker.onAppLeft(ms(30_000)))
        }
    }
}
