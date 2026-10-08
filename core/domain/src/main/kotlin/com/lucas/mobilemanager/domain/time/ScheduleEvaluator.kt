package com.lucas.mobilemanager.domain.time

import com.lucas.mobilemanager.domain.model.Schedule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Intervalle [start, end). */
data class TimeWindow(val start: Instant, val end: Instant) {
    operator fun contains(instant: Instant) = !instant.isBefore(start) && instant.isBefore(end)
}

/**
 * Évalue les plages horaires dans un fuseau donné.
 *
 * Changements d'heure : les heures locales sont résolues par [ZonedDateTime.of]. Une heure
 * inexistante (printemps) est décalée vers l'avant de la durée du trou ; une heure ambiguë
 * (automne) prend la première occurrence.
 */
object ScheduleEvaluator {

    /** Fenêtre de [schedule] qui contient [now], ou `null` si la plage est inactive. */
    fun activeWindow(schedule: Schedule, now: ZonedDateTime): TimeWindow? {
        val instant = now.toInstant()
        val today = now.toLocalDate()
        // Une plage qui passe minuit peut avoir commencé la veille.
        for (startDate in listOf(today, today.minusDays(1))) {
            if (startDate.dayOfWeek !in schedule.days) continue
            val endDate = if (schedule.crossesMidnight) startDate.plusDays(1) else startDate
            val window = TimeWindow(
                start = ZonedDateTime.of(startDate, schedule.start, now.zone).toInstant(),
                end = ZonedDateTime.of(endDate, schedule.end, now.zone).toInstant(),
            )
            if (instant in window) return window
        }
        return null
    }

    fun isActive(schedule: Schedule, now: ZonedDateTime): Boolean = activeWindow(schedule, now) != null

    /**
     * Fin de la période active formée par [schedules] autour de [now], en enchaînant les
     * plages contiguës (ex. 22h–00h puis 00h–06h → 06h). `null` si aucune plage n'est active.
     */
    fun activeUntil(schedules: List<Schedule>, now: ZonedDateTime): Instant? {
        var end: Instant = schedules.mapNotNull { activeWindow(it, now)?.end }.maxOrNull() ?: return null
        // Borne de sécurité : une semaine entière de plages 24 h ne doit pas boucler indéfiniment.
        repeat(MAX_CHAINED_WINDOWS) {
            val at = end.atZone(now.zone)
            val next = schedules.mapNotNull { activeWindow(it, at)?.end }.maxOrNull()
            if (next == null || !next.isAfter(end)) return end
            end = next
        }
        return end
    }

    private const val MAX_CHAINED_WINDOWS = 14
}

/** Bornes de la journée locale, pour le compteur quotidien remis à zéro à minuit. */
object DayBoundaries {
    fun startOfDay(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

    fun nextMidnight(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
}
