package com.lucas.mobilemanager.domain.zones

import java.time.Duration
import java.time.Instant

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

    private var attempts = 0
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

/** Visite terminée d'une zone autorisée, pour le tableau de bord. */
data class ZoneVisit(val packageName: String, val zone: String, val duration: Duration)

/** Mesure le temps passé dans les zones autorisées (Reels permis par un profil, par exemple). */
class ZoneVisitTracker(private val minDuration: Duration = Duration.ofSeconds(5)) {

    private var current: Triple<String, String, Instant>? = null

    /**
     * À appeler à chaque détection. [zone] `null` = hors zone. Renvoie la visite qui vient de se
     * terminer, si elle a duré au moins [minDuration].
     */
    fun onSnapshot(packageName: String, zone: String?, now: Instant): ZoneVisit? {
        val ongoing = current
        if (ongoing != null && ongoing.first == packageName && ongoing.second == zone) return null
        current = zone?.let { Triple(packageName, it, now) }
        return ongoing?.let { (pkg, z, start) ->
            Duration.between(start, now).takeIf { it >= minDuration }?.let { ZoneVisit(pkg, z, it) }
        }
    }

    /** L'app a quitté le premier plan. */
    fun onAppLeft(now: Instant): ZoneVisit? {
        val ongoing = current ?: return null
        current = null
        return Duration.between(ongoing.third, now).takeIf { it >= minDuration }?.let { ZoneVisit(ongoing.first, ongoing.second, it) }
    }
}
