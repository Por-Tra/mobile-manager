package com.lucas.mobilemanager.domain.model

import com.lucas.mobilemanager.domain.rules.Rule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Plage horaire hebdomadaire.
 * - `end <= start` : la plage passe minuit ; les [days] désignent le jour de **début**.
 * - `start == end` : plage de 24 h.
 * Début inclus, fin exclue.
 */
data class Schedule(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
) {
    val crossesMidnight: Boolean
        get() = !end.isAfter(start)

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
        val WEEKDAYS: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet()
    }
}

data class Profile(
    val id: Long = 0,
    val name: String,
    /** Plus grand = plus prioritaire parmi les profils planifiés. */
    val priority: Int,
    val schedules: List<Schedule>,
    val rules: List<Rule>,
    /** `false` : le profil ne s'active que manuellement, ses plages sont ignorées. */
    val autoActivation: Boolean = true,
    val enabled: Boolean = true,
)

/** Profil activé à la main, jusqu'à [until] (ou jusqu'à désactivation si `null`). */
data class ManualActivation(
    val profileId: Long,
    val until: Instant?,
)

/** Session de verrouillage strict. L'heure de fin est absolue pour survivre au redémarrage. */
data class StrictSession(
    val startedAt: Instant,
    val endsAt: Instant,
    val blockedPackages: Set<String>,
    /** Délai avant qu'une dérogation soit possible (logique en phase 4). */
    val reflectionDelay: Duration,
) {
    fun isRunning(now: Instant): Boolean = !now.isBefore(startedAt) && now.isBefore(endsAt)
}
