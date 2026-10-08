package com.lucas.mobilemanager.domain.time

import com.lucas.mobilemanager.domain.FALL_BACK
import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.PARIS
import com.lucas.mobilemanager.domain.SPRING_FORWARD
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.schedule
import com.lucas.mobilemanager.domain.time
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY as MON
import java.time.DayOfWeek.SUNDAY as SUN
import java.time.Duration
import java.time.ZonedDateTime

class ScheduleEvaluatorTest {

    private fun active(s: com.lucas.mobilemanager.domain.model.Schedule, now: ZonedDateTime) =
        ScheduleEvaluator.isActive(s, now)

    @Test
    fun `fixture dates fall on the expected days`() {
        assertEquals(DayOfWeek.MONDAY, MONDAY.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, SPRING_FORWARD.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, FALL_BACK.dayOfWeek)
    }

    @Nested
    inner class SameDayRange {
        private val workHours = schedule(time(9), time(18), *DayOfWeek.entries.take(5).toTypedArray())

        @Test
        fun `active inside the range`() = assertTrue(active(workHours, at(MONDAY, 10)))

        @Test
        fun `start is inclusive`() = assertTrue(active(workHours, at(MONDAY, 9)))

        @Test
        fun `end is exclusive`() = assertFalse(active(workHours, at(MONDAY, 18)))

        @Test
        fun `inactive before the range`() = assertFalse(active(workHours, at(MONDAY, 8, 59)))

        @Test
        fun `inactive on an unselected day`() = assertFalse(active(workHours, at(MONDAY.minusDays(2), 10)))

        @Test
        fun `window end is the same day`() {
            val window = ScheduleEvaluator.activeWindow(workHours, at(MONDAY, 10))
            assertEquals(at(MONDAY, 18).toInstant(), window?.end)
        }
    }

    @Nested
    inner class OvernightRange {
        // Dimanche 23 h → lundi 7 h, seul le dimanche est coché (jour de début).
        private val sundayNight = schedule(time(23), time(7), SUN)
        private val sunday = MONDAY.minusDays(1)

        @Test
        fun `active on the start day before midnight`() = assertTrue(active(sundayNight, at(sunday, 23, 30)))

        @Test
        fun `active on the next day after midnight`() = assertTrue(active(sundayNight, at(MONDAY, 3)))

        @Test
        fun `inactive at the end time`() = assertFalse(active(sundayNight, at(MONDAY, 7)))

        @Test
        fun `inactive after midnight when the previous day is unselected`() =
            assertFalse(active(sundayNight, at(sunday, 3)))

        @Test
        fun `inactive on the evening of an unselected day`() =
            assertFalse(active(sundayNight, at(MONDAY, 23, 30)))

        @Test
        fun `range ending at midnight ends at the next day start`() {
            val evening = schedule(time(18), time(0))
            val window = ScheduleEvaluator.activeWindow(evening, at(MONDAY, 23, 59))
            assertEquals(at(MONDAY.plusDays(1), 0).toInstant(), window?.end)
            assertFalse(active(evening, at(MONDAY.plusDays(1), 0)))
        }
    }

    @Nested
    inner class FullDayRange {
        private val mondayAllDay = schedule(time(0), time(0), MON)

        @Test
        fun `covers the whole day`() {
            assertTrue(active(mondayAllDay, at(MONDAY, 0)))
            assertTrue(active(mondayAllDay, at(MONDAY, 23, 59)))
        }

        @Test
        fun `stops at the next midnight`() = assertFalse(active(mondayAllDay, at(MONDAY.plusDays(1), 0)))
    }

    @Nested
    inner class DaylightSaving {
        private val sleep = schedule(time(0), time(6))

        @Test
        fun `spring forward - sleep window lasts five hours`() {
            val window = ScheduleEvaluator.activeWindow(sleep, at(SPRING_FORWARD, 5, 30))
            assertNotNull(window)
            assertEquals(Duration.ofHours(5), Duration.between(window!!.start, window.end))
            assertEquals(at(SPRING_FORWARD, 6).toInstant(), window.end)
        }

        @Test
        fun `spring forward - nonexistent start time is shifted forward`() {
            // 02:30 n'existe pas le 29 mars : la plage commence à 03:30.
            val night = schedule(time(2, 30), time(4))
            assertFalse(active(night, at(SPRING_FORWARD, 3, 15)))
            assertTrue(active(night, at(SPRING_FORWARD, 3, 45)))
        }

        @Test
        fun `fall back - sleep window lasts seven hours`() {
            val window = ScheduleEvaluator.activeWindow(sleep, at(FALL_BACK, 5))
            assertNotNull(window)
            assertEquals(Duration.ofHours(7), Duration.between(window!!.start, window.end))
        }

        @Test
        fun `fall back - repeated hour is covered on both occurrences`() {
            val night = schedule(time(2, 30), time(3))
            val firstOccurrence = at(FALL_BACK, 2, 45)
            val secondOccurrence = firstOccurrence.withLaterOffsetAtOverlap()
            assertTrue(active(night, firstOccurrence))
            assertTrue(active(night, secondOccurrence))
        }
    }

    @Nested
    inner class ActiveUntil {
        @Test
        fun `null when no schedule is active`() =
            assertNull(ScheduleEvaluator.activeUntil(listOf(schedule(time(9), time(10))), at(MONDAY, 12)))

        @Test
        fun `chains contiguous ranges across midnight`() {
            val schedules = listOf(schedule(time(22), time(0)), schedule(time(0), time(6)))
            assertEquals(
                at(MONDAY.plusDays(1), 6).toInstant(),
                ScheduleEvaluator.activeUntil(schedules, at(MONDAY, 23)),
            )
        }

        @Test
        fun `keeps the latest end of overlapping ranges`() {
            val schedules = listOf(schedule(time(9), time(12)), schedule(time(10), time(14)))
            assertEquals(at(MONDAY, 14).toInstant(), ScheduleEvaluator.activeUntil(schedules, at(MONDAY, 11)))
        }

        @Test
        fun `always-on schedule terminates`() {
            val allWeek = listOf(schedule(time(0), time(0)))
            assertNotNull(ScheduleEvaluator.activeUntil(allWeek, at(MONDAY, 12)))
        }
    }

    @Nested
    inner class Midnight {
        @Test
        fun `start of day and next midnight on a regular day`() {
            val now = at(MONDAY, 15).toInstant()
            assertEquals(at(MONDAY, 0).toInstant(), DayBoundaries.startOfDay(now, PARIS))
            assertEquals(at(MONDAY.plusDays(1), 0).toInstant(), DayBoundaries.nextMidnight(now, PARIS))
        }

        @Test
        fun `spring forward day lasts 23 hours`() {
            val now = at(SPRING_FORWARD, 12).toInstant()
            val length = Duration.between(DayBoundaries.startOfDay(now, PARIS), DayBoundaries.nextMidnight(now, PARIS))
            assertEquals(Duration.ofHours(23), length)
        }

        @Test
        fun `fall back day lasts 25 hours`() {
            val now = at(FALL_BACK, 12).toInstant()
            val length = Duration.between(DayBoundaries.startOfDay(now, PARIS), DayBoundaries.nextMidnight(now, PARIS))
            assertEquals(Duration.ofHours(25), length)
        }
    }
}
