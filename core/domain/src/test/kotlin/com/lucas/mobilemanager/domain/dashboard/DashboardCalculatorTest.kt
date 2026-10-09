package com.lucas.mobilemanager.domain.dashboard

import com.lucas.mobilemanager.domain.FALL_BACK
import com.lucas.mobilemanager.domain.INSTAGRAM
import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.PARIS
import com.lucas.mobilemanager.domain.TIKTOK
import com.lucas.mobilemanager.domain.YOUTUBE
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.EventType
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.zones.ZoneKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

class DashboardCalculatorTest {

    private val launcher = "com.google.android.apps.nexuslauncher"
    private fun min(m: Long) = Duration.ofMinutes(m)

    private fun input(
        period: Period = Period.TODAY,
        usage: DailyUsage = emptyMap(),
        events: List<EventLogEntry> = emptyList(),
        sessions: List<StrictSession> = emptyList(),
        goal: Duration? = null,
        zoneUsage: Map<LocalDate, Map<ZoneKey, Duration>> = emptyMap(),
        zoneQuotas: Map<ZoneKey, Duration> = emptyMap(),
    ) = DashboardInput(period, MONDAY, PARIS, usage, events, zoneUsage, zoneQuotas, sessions, goal, setOf(launcher))

    private fun event(type: EventType, day: LocalDate = MONDAY, hour: Int = 10, pkg: String? = null, zone: String? = null, details: String? = null) =
        EventLogEntry(type = type, occurredAt = at(day, hour).toInstant(), packageName = pkg, zone = zone, details = details)

    @Nested
    inner class Totals {
        private val usage: DailyUsage = mapOf(
            MONDAY to mapOf(YOUTUBE to min(40), INSTAGRAM to min(20), launcher to min(15)),
            MONDAY.minusDays(1) to mapOf(YOUTUBE to min(90)),
        )

        @Test
        fun `today total excludes the launcher and other days`() =
            assertEquals(min(60), DashboardCalculator.summarize(input(usage = usage)).total)

        @Test
        fun `apps are sorted by time with their share`() {
            val apps = DashboardCalculator.summarize(input(usage = usage)).apps
            assertEquals(listOf(YOUTUBE, INSTAGRAM), apps.map { it.packageName })
            assertEquals(2f / 3f, apps.first().share, 0.001f)
        }

        @Test
        fun `week sums the last seven days`() {
            val week = DashboardCalculator.summarize(input(Period.WEEK, usage)).total
            assertEquals(min(150), week)
        }

        @Test
        fun `days older than the week are ignored`() {
            val old = usage + (MONDAY.minusDays(7) to mapOf(TIKTOK to min(300)))
            assertEquals(min(150), DashboardCalculator.summarize(input(Period.WEEK, old)).total)
        }
    }

    @Nested
    inner class Comparisons {
        @Test
        fun `less than yesterday`() {
            val summary = DashboardCalculator.summarize(
                input(usage = mapOf(MONDAY to mapOf(YOUTUBE to min(30)), MONDAY.minusDays(1) to mapOf(YOUTUBE to min(70)))),
            )
            assertEquals(Comparison(Trend.LESS, min(40), min(70)), summary.comparison)
        }

        @Test
        fun `more than last week`() {
            val usage = mapOf(MONDAY to mapOf(YOUTUBE to min(100)), MONDAY.minusDays(8) to mapOf(YOUTUBE to min(40)))
            assertEquals(Comparison(Trend.MORE, min(60), min(40)), DashboardCalculator.summarize(input(Period.WEEK, usage)).comparison)
        }

        @Test
        fun `small differences count as the same`() {
            val usage = mapOf(MONDAY to mapOf(YOUTUBE to min(32)), MONDAY.minusDays(1) to mapOf(YOUTUBE to min(30)))
            assertEquals(Trend.SAME, DashboardCalculator.summarize(input(usage = usage)).comparison.trend)
        }

        @Test
        fun `no previous data means no comparison`() =
            assertEquals(Trend.NO_HISTORY, DashboardCalculator.summarize(input(usage = mapOf(MONDAY to mapOf(YOUTUBE to min(30))))).comparison.trend)

        @Test
        fun `a recorded day without usage is a real comparison`() {
            val usage = mapOf(MONDAY to mapOf(YOUTUBE to min(30)), MONDAY.minusDays(1) to emptyMap<String, Duration>())
            assertEquals(Trend.MORE, DashboardCalculator.summarize(input(usage = usage)).comparison.trend)
        }
    }

    @Nested
    inner class WeekChart {
        @Test
        fun `seven days ending today, missing days at zero`() {
            val week = DashboardCalculator.summarize(input(usage = mapOf(MONDAY.minusDays(2) to mapOf(YOUTUBE to min(25))))).week
            assertEquals((6L downTo 0L).map { MONDAY.minusDays(it) }, week.map { it.date })
            assertEquals(min(25), week[4].total)
            assertTrue(week[4].hasData)
            assertFalse(week[6].hasData)
        }

        @Test
        fun `empty period has no data`() = assertFalse(DashboardCalculator.summarize(input()).hasData)
    }

    @Nested
    inner class EventsAndGoals {
        private val events = listOf(
            event(EventType.APP_BLOCKED, details = "profile:2"),
            event(EventType.APP_BLOCKED, details = "daily_limit:30"),
            event(EventType.APP_BLOCKED, day = MONDAY.minusDays(2), details = "daily_limit:30"),
            // Ancien blocage anti-doomscrolling (historique) : compté comme une règle.
            event(EventType.APP_BLOCKED, details = "session_limit:10"),
            event(EventType.APP_BLOCKED, details = "strict_session"),
            event(EventType.INTERSTITIAL_SHOWN),
            event(EventType.ZONE_REDIRECTED, pkg = YOUTUBE, zone = "shorts"),
            event(EventType.ZONE_REDIRECTED, pkg = YOUTUBE, zone = "shorts"),
        )
        private val reels = ZoneKey(INSTAGRAM, "reels")
        private val zoneUsage = mapOf(
            MONDAY to mapOf(reels to min(5)),
            MONDAY.minusDays(1) to mapOf(reels to min(2)),
            MONDAY.minusDays(8) to mapOf(reels to min(99)),
        )

        @Test
        fun `interruptions are counted by kind for today only`() {
            val interruptions = DashboardCalculator.summarize(input(events = events)).interruptions
            assertEquals(
                mapOf(BlockKind.RULE to 2, BlockKind.DAILY_LIMIT to 1, BlockKind.STRICT_SESSION to 1),
                interruptions.blocks,
            )
            assertEquals(2, interruptions.zoneExits)
            assertEquals(6, interruptions.total)
        }

        @Test
        fun `zone time and exits`() {
            val week = input(Period.WEEK, events = events, zoneUsage = zoneUsage, zoneQuotas = mapOf(reels to min(50)))
            val zones = DashboardCalculator.summarize(week).zones
            assertEquals(mapOf(reels to min(7)), zones.timeByZone)
            assertEquals(mapOf(ZoneKey(YOUTUBE, "shorts") to 2), zones.exitsByZone)
            assertEquals(mapOf(reels to min(50)), zones.quotaByZone)
            assertEquals(mapOf(reels to min(5)), DashboardCalculator.summarize(input(zoneUsage = zoneUsage)).zones.timeByZone)
        }

        @Test
        fun `days with a reached daily limit are counted once per day`() =
            assertEquals(2, DashboardCalculator.summarize(input(Period.WEEK, events = events)).goals.daysLimitReached)

        @Test
        fun `days under the daily goal`() {
            val usage = mapOf(
                MONDAY to mapOf(YOUTUBE to min(100)),
                MONDAY.minusDays(1) to mapOf(YOUTUBE to min(200)),
                MONDAY.minusDays(2) to mapOf(YOUTUBE to min(180)),
            )
            val goals = DashboardCalculator.summarize(input(Period.WEEK, usage, goal = Duration.ofHours(3))).goals
            assertEquals(2, goals.daysUnderGoal)
            assertEquals(3, goals.daysWithData)
        }

        @Test
        fun `no goal means no goal count`() = assertNull(DashboardCalculator.summarize(input()).goals.daysUnderGoal)

        @Test
        fun `concentration sessions started in the period`() {
            fun session(day: LocalDate, reason: StrictSession.EndReason) = StrictSession(
                startedAt = at(day, 9).toInstant(),
                endsAt = at(day, 10).toInstant(),
                blockedPackages = setOf(TIKTOK),
                reflectionDelay = min(5),
                endedAt = at(day, 10).toInstant(),
                endReason = reason,
            )
            val sessions = listOf(
                session(MONDAY, StrictSession.EndReason.COMPLETED),
                session(MONDAY.minusDays(3), StrictSession.EndReason.COMPLETED),
                session(MONDAY.minusDays(1), StrictSession.EndReason.OVERRIDDEN),
                session(MONDAY.minusDays(9), StrictSession.EndReason.OVERRIDDEN),
            )
            val goals = DashboardCalculator.summarize(input(Period.WEEK, sessions = sessions)).goals
            assertEquals(2, goals.sessionsCompleted)
            assertEquals(1, goals.sessionsEndedEarly)
        }
    }

    @Nested
    inner class Ranges {
        @Test
        fun `week ends today and previous week is just before`() {
            val week = PeriodRange.of(Period.WEEK, MONDAY)
            assertEquals(DateRange(MONDAY.minusDays(6), MONDAY), week)
            assertEquals(DateRange(MONDAY.minusDays(13), MONDAY.minusDays(7)), PeriodRange.previous(week))
            assertEquals(DateRange(MONDAY.minusDays(1), MONDAY.minusDays(1)), PeriodRange.previous(PeriodRange.of(Period.TODAY, MONDAY)))
        }

        @Test
        fun `range instants follow local days across a clock change`() {
            val (from, to) = DateRange(FALL_BACK, FALL_BACK).instants(PARIS)
            assertEquals(Duration.ofHours(25), Duration.between(from, to))
        }
    }
}
