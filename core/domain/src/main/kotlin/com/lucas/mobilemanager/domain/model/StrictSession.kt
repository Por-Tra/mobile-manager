package com.lucas.mobilemanager.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Session de verrouillage strict. Toutes les heures sont absolues pour survivre au redémarrage.
 */
data class StrictSession(
    val id: Long = 0,
    val startedAt: Instant,
    val endsAt: Instant,
    val blockedPackages: Set<String>,
    /** Délai de réflexion avant qu'une dérogation soit possible, choisi avant la session. */
    val reflectionDelay: Duration,
    /** Début du délai de réflexion, si une dérogation a été demandée. */
    val overrideRequestedAt: Instant? = null,
    /** Fin effective (heure de fin atteinte ou dérogation), `null` tant que la session n'est pas close. */
    val endedAt: Instant? = null,
    val endReason: EndReason? = null,
) {
    enum class EndReason { COMPLETED, OVERRIDDEN }

    /** La session bloque en ce moment : commencée, pas encore à son heure de fin, ni close. */
    fun isRunning(now: Instant): Boolean =
        endedAt == null && !now.isBefore(startedAt) && now.isBefore(endsAt)
}
