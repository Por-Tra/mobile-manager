package com.lucas.mobilemanager.domain.time

import java.time.LocalTime
import java.time.ZonedDateTime

object LocalTimes {
    /**
     * Prochaine occurrence de [time] après [now] : aujourd'hui si elle n'est pas encore passée,
     * sinon demain. Une heure inexistante (passage à l'heure d'été) est décalée vers l'avant.
     */
    fun nextOccurrence(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
        return if (today.isAfter(now)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
    }
}
