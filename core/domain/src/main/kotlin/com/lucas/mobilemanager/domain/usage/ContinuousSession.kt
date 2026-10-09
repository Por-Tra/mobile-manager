package com.lucas.mobilemanager.domain.usage

import java.time.Duration
import java.time.Instant

/** Session continue en cours sur une app : depuis [start], au premier plan maintenant. */
data class ContinuousSession(val start: Instant, val length: Duration)

object ContinuousSessions {

    /**
     * Session continue en cours de [packageName], ou `null` si l'app n'est pas au premier plan.
     *
     * Les passages au premier plan sont fusionnés tant que l'interruption (sortie de l'app, écran
     * éteint, écran de pause) dure moins que [gap]. La durée est mesurée depuis le début de la
     * session fusionnée, interruptions courtes comprises.
     *
     * @param from début de la fenêtre d'événements : une pause sans reprise préalable signifie que
     *   l'app était déjà au premier plan à [from].
     */
    fun current(
        events: List<UsageEvent>,
        packageName: String,
        from: Instant,
        now: Instant,
        gap: Duration,
    ): ContinuousSession? {
        val intervals = foregroundIntervals(events.filter { it.packageName == packageName }, from, now)
        val last = intervals.lastOrNull() ?: return null
        if (last.second != null) return null // l'app n'est plus au premier plan

        var start = last.first
        for (i in intervals.lastIndex - 1 downTo 0) {
            val (_, end) = intervals[i]
            val interruption = Duration.between(end!!, start)
            if (interruption >= gap) break
            start = intervals[i].first
        }
        return ContinuousSession(start, Duration.between(start, now).coerceAtLeast(Duration.ZERO))
    }

    /** Intervalles [début, fin) au premier plan, triés ; fin `null` = encore au premier plan. */
    private fun foregroundIntervals(events: List<UsageEvent>, from: Instant, now: Instant): List<Pair<Instant, Instant?>> {
        val intervals = mutableListOf<Pair<Instant, Instant?>>()
        var openSince: Instant? = null
        var seenEvent = false
        for (event in events.sortedBy { it.timestamp }) {
            if (event.timestamp.isAfter(now)) break
            when (event.type) {
                UsageEvent.Type.RESUMED -> if (openSince == null) openSince = event.timestamp
                UsageEvent.Type.PAUSED -> {
                    val start = openSince ?: if (!seenEvent) from else null
                    if (start != null) intervals += start to event.timestamp
                    openSince = null
                }
            }
            seenEvent = true
        }
        openSince?.let { intervals += it to null }
        return intervals
    }
}
