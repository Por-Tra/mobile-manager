package com.lucas.mobilemanager.domain.zones

import com.lucas.mobilemanager.domain.time.DayBoundaries
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Évite les boucles quand on sort d'une zone : au plus une action toutes les [minInterval], et
 * repli sur l'écran de blocage si la zone persiste après [maxAttempts] actions (détecteur qui
 * clique au mauvais endroit, app qui ignore le retour…).
 */
class ZoneExitThrottle(
    private val minInterval: Duration = Duration.ofMillis(700),
    private val maxAttempts: Int = 3,
) {
    enum class Action { EXIT, WAIT, ESCALATE }

    /** Nombre de sorties tentées depuis l'entrée dans la zone (la première vaut 1). */
    var attempts = 0
        private set
    private var lastActionAt: Instant? = null

    fun onZoneDetected(now: Instant): Action {
        val last = lastActionAt
        if (last != null && Duration.between(last, now) < minInterval) return Action.WAIT
        lastActionAt = now
        attempts++
        return if (attempts > maxAttempts) Action.ESCALATE else Action.EXIT
    }

    /** La zone n'est plus à l'écran : la sortie a réussi. */
    fun onZoneLeft() {
        attempts = 0
        lastActionAt = null
    }
}

/** Une zone d'une app (Reels d'Instagram…). */
data class ZoneKey(val packageName: String, val zone: String)

/** Temps passé dans une zone pendant un jour local. */
data class ZoneUsageDelta(val date: LocalDate, val packageName: String, val zone: String, val duration: Duration)

/**
 * Mesure le temps passé dans les zones (Reels, Shorts…) pour les quotas quotidiens.
 *
 * Le temps entre deux constats consécutifs « dans la zone » est compté, plafonné à [maxGap] :
 * un écran figé ou un service suspendu ne compte pas indéfiniment. Le temps est coupé à minuit
 * pour être attribué au bon jour.
 */
class ZoneUsageAccumulator(private val maxGap: Duration = Duration.ofSeconds(15)) {

    private data class Ongoing(val packageName: String, val zone: String, val since: Instant)

    private var ongoing: Ongoing? = null

    /** Constat à [now] : dans [zone] de [packageName], ou hors zone (`null`). Renvoie le temps à ajouter. */
    fun onSnapshot(packageName: String, zone: String?, now: Instant, timeZone: ZoneId): List<ZoneUsageDelta> {
        val deltas = close(now, timeZone)
        ongoing = zone?.let { Ongoing(packageName, it, now) }
        return deltas
    }

    /** L'app a quitté le premier plan (ou l'écran s'est éteint). */
    fun onLeft(now: Instant, timeZone: ZoneId): List<ZoneUsageDelta> = close(now, timeZone).also { ongoing = null }

    private fun close(now: Instant, timeZone: ZoneId): List<ZoneUsageDelta> {
        val current = ongoing ?: return emptyList()
        if (!now.isAfter(current.since)) return emptyList()
        val end = minOf(now, current.since + maxGap)
        val deltas = ArrayList<ZoneUsageDelta>(1)
        var start = current.since
        while (start.isBefore(end)) {
            val date = start.atZone(timeZone).toLocalDate()
            val sliceEnd = minOf(end, DayBoundaries.nextMidnight(start, timeZone))
            deltas += ZoneUsageDelta(date, current.packageName, current.zone, Duration.between(start, sliceEnd))
            start = sliceEnd
        }
        return deltas
    }
}
