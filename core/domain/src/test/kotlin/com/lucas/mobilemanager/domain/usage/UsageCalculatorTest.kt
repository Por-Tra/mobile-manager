package com.lucas.mobilemanager.domain.usage

import com.lucas.mobilemanager.domain.FALL_BACK
import com.lucas.mobilemanager.domain.INSTAGRAM
import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.PARIS
import com.lucas.mobilemanager.domain.YOUTUBE
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.time.DayBoundaries
import com.lucas.mobilemanager.domain.usage.UsageEvent.Type.PAUSED
import com.lucas.mobilemanager.domain.usage.UsageEvent.Type.RESUMED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

class UsageCalculatorTest {

    private val midnight = at(MONDAY, 0).toInstant()

    private fun event(pkg: String, type: UsageEvent.Type, hour: Int, minute: Int = 0, date: LocalDate = MONDAY) =
        UsageEvent(pkg, type, at(date, hour, minute).toInstant())

    private fun usage(events: List<UsageEvent>, nowHour: Int, nowMinute: Int = 0, pkg: String = INSTAGRAM) =
        UsageCalculator.usageSince(events, pkg, midnight, at(MONDAY, nowHour, nowMinute).toInstant())

    @Test
    fun `sums closed sessions`() {
        val events = listOf(
            event(INSTAGRAM, RESUMED, 9), event(INSTAGRAM, PAUSED, 9, 10),
            event(INSTAGRAM, RESUMED, 12), event(INSTAGRAM, PAUSED, 12, 5),
        )
        assertEquals(Duration.ofMinutes(15), usage(events, 14))
    }

    @Test
    fun `open session counts until now`() {
        val events = listOf(event(INSTAGRAM, RESUMED, 10))
        assertEquals(Duration.ofMinutes(25), usage(events, 10, 25))
    }

    @Test
    fun `session started yesterday is clipped at midnight`() {
        val events = listOf(
            event(INSTAGRAM, RESUMED, 23, 30, date = MONDAY.minusDays(1)),
            event(INSTAGRAM, PAUSED, 0, 20),
        )
        assertEquals(Duration.ofMinutes(20), usage(events, 8))
    }

    @Test
    fun `app still open since yesterday counts from midnight`() {
        val events = listOf(event(INSTAGRAM, RESUMED, 23, date = MONDAY.minusDays(1)))
        assertEquals(Duration.ofMinutes(30), usage(events, 0, 30))
    }

    @Test
    fun `leading pause without resume counts from the start of the window`() {
        val events = listOf(event(INSTAGRAM, PAUSED, 0, 10))
        assertEquals(Duration.ofMinutes(10), usage(events, 9))
    }

    @Test
    fun `extra pauses after a session are ignored`() {
        val events = listOf(
            event(INSTAGRAM, RESUMED, 9), event(INSTAGRAM, PAUSED, 9, 10),
            event(INSTAGRAM, PAUSED, 9, 30),
        )
        assertEquals(Duration.ofMinutes(10), usage(events, 12))
    }

    @Test
    fun `repeated resumes keep the first start`() {
        val events = listOf(
            event(INSTAGRAM, RESUMED, 9), event(INSTAGRAM, RESUMED, 9, 5),
            event(INSTAGRAM, PAUSED, 9, 10),
        )
        assertEquals(Duration.ofMinutes(10), usage(events, 12))
    }

    @Test
    fun `unsorted events are handled`() {
        val events = listOf(event(INSTAGRAM, PAUSED, 9, 10), event(INSTAGRAM, RESUMED, 9))
        assertEquals(Duration.ofMinutes(10), usage(events, 12))
    }

    @Test
    fun `other packages are ignored`() {
        val events = listOf(
            event(YOUTUBE, RESUMED, 9), event(YOUTUBE, PAUSED, 10),
            event(INSTAGRAM, RESUMED, 10), event(INSTAGRAM, PAUSED, 10, 5),
        )
        assertEquals(Duration.ofMinutes(5), usage(events, 12))
    }

    @Test
    fun `usage by package splits time per app`() {
        val events = listOf(
            event(YOUTUBE, RESUMED, 9), event(YOUTUBE, PAUSED, 10),
            event(INSTAGRAM, RESUMED, 10), event(INSTAGRAM, PAUSED, 10, 5),
        )
        assertEquals(
            mapOf(YOUTUBE to Duration.ofHours(1), INSTAGRAM to Duration.ofMinutes(5)),
            UsageCalculator.usageByPackage(events, midnight, at(MONDAY, 12).toInstant()),
        )
    }

    @Test
    fun `fall back night counts real elapsed time`() {
        // 01:30 → 03:30 le 25 octobre : 3 heures réelles (l'heure 02:00–03:00 est vécue deux fois).
        val from = DayBoundaries.startOfDay(at(FALL_BACK, 12).toInstant(), PARIS)
        val events = listOf(
            UsageEvent(INSTAGRAM, RESUMED, at(FALL_BACK, 1, 30).toInstant()),
            UsageEvent(INSTAGRAM, PAUSED, at(FALL_BACK, 3, 30).toInstant()),
        )
        assertEquals(
            Duration.ofHours(3),
            UsageCalculator.usageSince(events, INSTAGRAM, from, at(FALL_BACK, 12).toInstant()),
        )
    }

    @Test
    fun `app left open without a pause is closed when another app opens`() {
        // Coupure brutale : la pause d'Instagram n'a jamais été enregistrée.
        val events = listOf(
            event(INSTAGRAM, RESUMED, 9),
            event(YOUTUBE, RESUMED, 9, 10), event(YOUTUBE, PAUSED, 9, 30),
        )
        assertEquals(
            mapOf(INSTAGRAM to Duration.ofMinutes(10), YOUTUBE to Duration.ofMinutes(20)),
            UsageCalculator.usageByPackage(events, midnight, at(MONDAY, 12).toInstant()),
        )
    }

    @Test
    fun `device shutdown closes the app in the foreground`() {
        val shutdown = UsageEvent("android", UsageEvent.Type.INTERRUPTION, at(MONDAY, 9, 15).toInstant())
        val events = listOf(event(INSTAGRAM, RESUMED, 9), shutdown)
        assertEquals(mapOf(INSTAGRAM to Duration.ofMinutes(15)), UsageCalculator.usageByPackage(events, midnight, at(MONDAY, 12).toInstant()))
        assertEquals(Duration.ofMinutes(15), usage(events, 12))
    }

    @Test
    fun `late pause of an app already replaced is ignored`() {
        val events = listOf(
            event(INSTAGRAM, RESUMED, 9),
            event(YOUTUBE, RESUMED, 9, 10),
            event(INSTAGRAM, PAUSED, 9, 11),
            event(YOUTUBE, PAUSED, 9, 20),
        )
        assertEquals(
            mapOf(INSTAGRAM to Duration.ofMinutes(10), YOUTUBE to Duration.ofMinutes(10)),
            UsageCalculator.usageByPackage(events, midnight, at(MONDAY, 12).toInstant()),
        )
    }
}
