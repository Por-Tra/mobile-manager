package com.lucas.mobilemanager.domain.usage

import java.time.Duration
import java.time.Instant

/** Événement de premier plan, tel que remonté par `UsageStatsManager.queryEvents()`. */
data class UsageEvent(
    val packageName: String,
    val type: Type,
    val timestamp: Instant,
) {
    enum class Type {
        RESUMED,
        PAUSED,

        /**
         * Arrêt ou démarrage de l'appareil, écran éteint : plus aucune app n'est au premier plan.
         * Sans cela, une app ouverte lors d'une coupure brutale (batterie, plantage) serait
         * comptée comme ouverte indéfiniment, la pause n'ayant jamais été enregistrée.
         */
        INTERRUPTION,
    }
}

/**
 * Temps passé au premier plan, calculé à partir des événements reprise/pause.
 *
 * Les événements doivent couvrir un peu plus que la période voulue (quelques heures avant
 * minuit) pour retrouver la reprise d'une app restée ouverte à cheval sur minuit.
 */
object UsageCalculator {

    /**
     * Temps passé dans chaque app sur `[from, now)`, en suivant la chronologie globale : une
     * seule app est au premier plan à la fois, donc l'ouverture d'une app clôt la précédente,
     * même si sa pause n'a pas été enregistrée.
     */
    fun usageByPackage(events: List<UsageEvent>, from: Instant, now: Instant): Map<String, Duration> {
        val totals = HashMap<String, Duration>()
        var open: Pair<String, Instant>? = null
        var seenEvent = false

        fun add(pkg: String, start: Instant, end: Instant) {
            val duration = clipped(start, end, from, now)
            if (!duration.isZero) totals.merge(pkg, duration, Duration::plus)
        }

        fun close(at: Instant) {
            open?.let { (pkg, since) -> add(pkg, since, at) }
            open = null
        }

        for (event in events.sortedBy { it.timestamp }) {
            when (event.type) {
                UsageEvent.Type.RESUMED -> if (open?.first != event.packageName) {
                    close(event.timestamp)
                    open = event.packageName to event.timestamp
                }
                UsageEvent.Type.PAUSED -> when {
                    open?.first == event.packageName -> close(event.timestamp)
                    // Toute première trace : l'app était déjà au premier plan au début de la fenêtre.
                    open == null && !seenEvent -> add(event.packageName, from, event.timestamp)
                }
                UsageEvent.Type.INTERRUPTION -> close(event.timestamp)
            }
            seenEvent = true
        }
        close(now)
        return totals
    }

    fun usageSince(events: List<UsageEvent>, packageName: String, from: Instant, now: Instant): Duration =
        sumSessions(events.filter { it.packageName == packageName || it.type == UsageEvent.Type.INTERRUPTION }, from, now)

    private fun sumSessions(events: List<UsageEvent>, from: Instant, now: Instant): Duration {
        var total = Duration.ZERO
        var openSince: Instant? = null
        var seenEvent = false

        for (event in events.sortedBy { it.timestamp }) {
            when (event.type) {
                UsageEvent.Type.RESUMED -> if (openSince == null) openSince = event.timestamp
                UsageEvent.Type.PAUSED -> {
                    val start = openSince ?: if (!seenEvent) from else null
                    if (start != null) total += clipped(start, event.timestamp, from, now)
                    openSince = null
                }
                UsageEvent.Type.INTERRUPTION -> {
                    openSince?.let { total += clipped(it, event.timestamp, from, now) }
                    openSince = null
                }
            }
            seenEvent = true
        }
        openSince?.let { total += clipped(it, now, from, now) }
        return total
    }

    /** Durée de `[start, end)` ∩ `[from, now)`. */
    private fun clipped(start: Instant, end: Instant, from: Instant, now: Instant): Duration {
        val s = maxOf(start, from)
        val e = minOf(end, now)
        return if (e.isAfter(s)) Duration.between(s, e) else Duration.ZERO
    }
}
