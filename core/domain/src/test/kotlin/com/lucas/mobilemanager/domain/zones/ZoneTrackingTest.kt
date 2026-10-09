package com.lucas.mobilemanager.domain.zones

import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.PARIS
import com.lucas.mobilemanager.domain.at
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

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
    inner class Usage {
        private val counter = ZoneUsageAccumulator()
        private fun instant(hour: Int, minute: Int, second: Int = 0) = at(MONDAY, hour, minute).toInstant().plusSeconds(second.toLong())
        private fun delta(seconds: Long, date: LocalDate = MONDAY) = ZoneUsageDelta(date, "ig", "reels", Duration.ofSeconds(seconds))

        @Test
        fun `time between consecutive snapshots in the zone is counted`() {
            assertEquals(emptyList<ZoneUsageDelta>(), counter.onSnapshot("ig", "reels", instant(10, 0), PARIS))
            assertEquals(listOf(delta(5)), counter.onSnapshot("ig", "reels", instant(10, 0, 5), PARIS))
            assertEquals(listOf(delta(5)), counter.onSnapshot("ig", "reels", instant(10, 0, 10), PARIS))
        }

        @Test
        fun `a long gap counts at most 15 seconds`() {
            counter.onSnapshot("ig", "reels", instant(10, 0), PARIS)
            assertEquals(listOf(delta(15)), counter.onSnapshot("ig", "reels", instant(10, 5), PARIS))
        }

        @Test
        fun `leaving the zone counts until the last snapshot, then stops`() {
            counter.onSnapshot("ig", "reels", instant(10, 0), PARIS)
            assertEquals(listOf(delta(4)), counter.onSnapshot("ig", null, instant(10, 0, 4), PARIS))
            assertEquals(emptyList<ZoneUsageDelta>(), counter.onSnapshot("ig", null, instant(10, 0, 9), PARIS))
        }

        @Test
        fun `leaving the app closes the zone`() {
            counter.onSnapshot("ig", "reels", instant(10, 0), PARIS)
            assertEquals(listOf(delta(3)), counter.onLeft(instant(10, 0, 3), PARIS))
            assertEquals(emptyList<ZoneUsageDelta>(), counter.onLeft(instant(10, 0, 8), PARIS))
        }

        @Test
        fun `switching zone credits the previous one`() {
            counter.onSnapshot("ig", "reels", instant(10, 0), PARIS)
            assertEquals(listOf(delta(2)), counter.onSnapshot("yt", "shorts", instant(10, 0, 2), PARIS))
            assertEquals(
                listOf(ZoneUsageDelta(MONDAY, "yt", "shorts", Duration.ofSeconds(5))),
                counter.onSnapshot("yt", "shorts", instant(10, 0, 7), PARIS),
            )
        }

        @Test
        fun `time is split at midnight`() {
            val beforeMidnight = at(MONDAY.minusDays(1), 23, 59).toInstant().plusSeconds(55)
            counter.onSnapshot("ig", "reels", beforeMidnight, PARIS)
            assertEquals(
                listOf(delta(5, MONDAY.minusDays(1)), delta(5)),
                counter.onSnapshot("ig", "reels", beforeMidnight.plusSeconds(10), PARIS),
            )
        }
    }
}
