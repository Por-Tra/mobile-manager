package com.lucas.mobilemanager.domain.usage

import java.time.Duration
import java.time.Instant

/** Événement de premier plan, tel que remonté par `UsageStatsManager.queryEvents()`. */
data class UsageEvent(
    val packageName: String,
    val type: Type,
    val timestamp: Instant,
) {
    enum class Type { RESUMED, PAUSED }
}

/**
 * Temps passé au premier plan, calculé à partir des événements reprise/pause.
 *
 * Les événements doivent couvrir un peu plus que la période voulue (quelques heures avant
 * minuit) pour retrouver la reprise d'une app restée ouverte à cheval sur minuit.
 */
object UsageCalculator {

    /** Temps passé dans chaque app sur `[from, now)`. */
    fun usageByPackage(events: List<UsageEvent>, from: Instant, now: Instant): Map<String, Duration> =
        events.groupBy { it.packageName }
            .mapValues { (_, packageEvents) -> sumSessions(packageEvents, from, now) }
            .filterValues { !it.isZero }

    fun usageSince(events: List<UsageEvent>, packageName: String, from: Instant, now: Instant): Duration =
        sumSessions(events.filter { it.packageName == packageName }, from, now)

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
