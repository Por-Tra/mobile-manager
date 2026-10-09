package com.lucas.mobilemanager.domain.model

import java.time.Instant

/** Événement horodaté de l'historique, utilisé par le tableau de bord. */
data class EventLogEntry(
    val id: Long = 0,
    val type: EventType,
    val occurredAt: Instant,
    val packageName: String? = null,
    val zone: String? = null,
    val details: String? = null,
)

enum class EventType {
    APP_BLOCKED,
    ZONE_REDIRECTED,
    /** Historique (phase 7) : remplacé par la table `zone_usage`. Gardé pour relire le journal. */
    ZONE_VISIT,
    /** Historique (anti-doomscrolling, retiré) : gardé pour relire le journal. */
    INTERSTITIAL_SHOWN,
    /** Historique (anti-doomscrolling, retiré) : gardé pour relire le journal. */
    PAUSE_CLOSED_APP,
    STRICT_SESSION_STARTED,
    STRICT_SESSION_ENDED,
    STRICT_SESSION_OVERRIDDEN,
    PROTECTION_DISABLED,
}
