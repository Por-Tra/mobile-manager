package com.lucas.mobilemanager.domain.rules

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Règles de rythme de la surveillance, indépendantes d'Android. */
object EnforcementPolicy {

    /** Intervalle maximal entre deux réévaluations d'une app restée ouverte. */
    val MAX_CHECK_INTERVAL: Duration = Duration.ofSeconds(30)

    private val MIN_CHECK_INTERVAL: Duration = Duration.ofSeconds(1)

    /**
     * Délai avant de réévaluer l'app au premier plan, ou `null` si elle est bloquée ou en pause.
     * Une app autorisée est revue au plus tard toutes les 30 s (début d'une plage horaire),
     * pile au moment où sa limite quotidienne est atteinte et au prochain palier anti-doomscrolling.
     */
    fun nextCheckDelay(decision: Decision): Duration? = when (decision) {
        is Decision.Blocked, is Decision.Pause -> null
        is Decision.Allowed -> listOfNotNull(decision.remainingToday, decision.checkAgainIn, MAX_CHECK_INTERVAL)
            .min()
            .coerceIn(MIN_CHECK_INTERVAL, MAX_CHECK_INTERVAL)
    }
}

/** Position de la fin d'un blocage par rapport à aujourd'hui, pour un message lisible. */
enum class DayRelation { TODAY, TOMORROW, LATER }

object BlockEnd {
    fun relation(until: Instant, now: Instant, zone: ZoneId): DayRelation {
        val today = now.atZone(zone).toLocalDate()
        val endDay = until.atZone(zone).toLocalDate()
        return when {
            !endDay.isAfter(today) -> DayRelation.TODAY
            endDay == today.plusDays(1) -> DayRelation.TOMORROW
            else -> DayRelation.LATER
        }
    }
}
