package com.lucas.mobilemanager.domain.doomscroll

import com.lucas.mobilemanager.domain.rules.BlockReason
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.time.DayBoundaries
import com.lucas.mobilemanager.domain.usage.ContinuousSession
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Réglage anti-doomscrolling d'une app. Chaque garde-fou est facultatif. */
data class ScrollGuard(
    val packageName: String,
    /** Écran de pause toutes les X min de session continue. */
    val pauseEvery: Duration? = null,
    /** Blocage après X min de session continue… */
    val sessionLimit: Duration? = null,
    /** …pendant ce temps de repos. */
    val cooldown: Duration = Duration.ofMinutes(15),
    /** Limite quotidienne propre à l'anti-doomscrolling (ex. TikTok). */
    val dailyLimit: Duration? = null,
)

/** Réglages généraux de l'anti-doomscrolling. */
data class ScrollGuardSettings(
    val enabled: Boolean = true,
    /** Une session continue se termine après cette durée hors de l'app. */
    val sessionGap: Duration = Duration.ofSeconds(60),
    /** Durée pendant laquelle l'écran de pause ne propose aucun bouton. */
    val pauseDuration: Duration = Duration.ofSeconds(10),
    /** Rappel affiché pendant la pause (ex. « Lire 20 pages »). */
    val goal: String = "",
)

sealed interface GuardDecision {
    /** Rien à faire ; revoir l'app dans [checkAgainIn] (prochain palier), `null` si sans objet. */
    data class None(val checkAgainIn: Duration?) : GuardDecision

    /** Afficher l'écran de pause. */
    data class Pause(val sessionLength: Duration) : GuardDecision

    data class Blocked(val reason: BlockReason, val until: Instant) : GuardDecision
}

object ScrollGuardPolicy {

    /**
     * @param session session continue en cours (`null` si l'app n'est pas au premier plan).
     * @param lastPauseAt dernier écran de pause affiché pour cette app.
     * @param cooldownUntil fin du temps de repos en cours, s'il y en a un.
     */
    fun evaluate(
        guard: ScrollGuard,
        session: ContinuousSession?,
        usageToday: Duration,
        lastPauseAt: Instant?,
        cooldownUntil: Instant?,
        now: Instant,
        zone: ZoneId,
    ): GuardDecision {
        if (cooldownUntil != null && now.isBefore(cooldownUntil)) {
            return GuardDecision.Blocked(BlockReason.SessionLimitReached(guard.sessionLimit ?: Duration.ZERO), cooldownUntil)
        }
        guard.dailyLimit?.let { limit ->
            if (usageToday >= limit) {
                return GuardDecision.Blocked(BlockReason.DailyLimitReached(limit), DayBoundaries.nextMidnight(now, zone))
            }
        }
        val length = session?.length ?: Duration.ZERO
        guard.sessionLimit?.let { limit ->
            if (session != null && length >= limit) {
                return GuardDecision.Blocked(BlockReason.SessionLimitReached(limit), now + guard.cooldown)
            }
        }
        val pauseEvery = guard.pauseEvery?.takeIf { !it.isZero && !it.isNegative }
        if (session != null && pauseEvery != null) {
            val milestones = length.toMillis() / pauseEvery.toMillis()
            if (milestones >= 1) {
                val milestoneAt = session.start + pauseEvery.multipliedBy(milestones)
                if (lastPauseAt == null || lastPauseAt.isBefore(milestoneAt)) return GuardDecision.Pause(length)
            }
        }
        return GuardDecision.None(nextCheck(guard, session, usageToday, pauseEvery))
    }

    /**
     * Applique l'anti-doomscrolling **après** le moteur de règles : il n'ajoute que des
     * restrictions. Une app bloquée par un profil le reste ; une app autorisée (même
     * explicitement par un profil) reste soumise aux pauses et limites de session.
     */
    fun combine(engine: Decision, guard: GuardDecision): Decision = when (engine) {
        is Decision.Blocked, is Decision.Pause -> engine
        is Decision.Allowed -> when (guard) {
            is GuardDecision.Blocked -> Decision.Blocked(guard.reason, guard.until)
            is GuardDecision.Pause -> Decision.Pause(guard.sessionLength)
            is GuardDecision.None -> engine.copy(checkAgainIn = guard.checkAgainIn)
        }
    }

    /** Délai jusqu'au prochain événement : palier de pause, limite de session ou limite quotidienne. */
    private fun nextCheck(guard: ScrollGuard, session: ContinuousSession?, usageToday: Duration, pauseEvery: Duration?): Duration? {
        val candidates = mutableListOf<Duration>()
        guard.dailyLimit?.let { candidates += it - usageToday }
        if (session != null) {
            guard.sessionLimit?.let { candidates += it - session.length }
            pauseEvery?.let {
                val next = it.multipliedBy(session.length.toMillis() / it.toMillis() + 1)
                candidates += next - session.length
            }
        }
        return candidates.filter { !it.isNegative }.minOrNull()
    }
}
