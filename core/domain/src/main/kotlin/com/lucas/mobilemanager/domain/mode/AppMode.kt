package com.lucas.mobilemanager.domain.mode

import java.time.Duration
import java.time.Instant

/** Mode choisi explicitement ; aucun changement automatique selon l'usage. */
enum class AppMode { FREE, SEMI_FREE, RESTRICTED }

/** Sans choix enregistré, aucun blocage. La demande conserve son heure après redémarrage. */
data class ModeSettings(
    val selectedMode: AppMode? = null,
    val minimumWaitMinutes: Int = 60,
    val unlockRequestedAt: Instant? = null,
) {
    val effectiveMode: AppMode get() = selectedMode ?: AppMode.FREE

    init {
        require(minimumWaitMinutes in 1..1440)
    }
}

object ModePolicy {
    fun blocksEnabled(settings: ModeSettings): Boolean = settings.effectiveMode != AppMode.FREE

    fun requiresWait(settings: ModeSettings, hasPin: Boolean): Boolean =
        settings.effectiveMode == AppMode.SEMI_FREE && !hasPin

    fun canModify(settings: ModeSettings, hasPin: Boolean, unlockedUntil: Instant?, now: Instant): Boolean =
        unlockedUntil?.let(now::isBefore) == true || !hasPin && !requiresWait(settings, hasPin = false)

    fun readyAt(settings: ModeSettings): Instant? =
        settings.unlockRequestedAt?.plus(Duration.ofMinutes(settings.minimumWaitMinutes.toLong()))

    fun canUnlockAfterWait(settings: ModeSettings, hasPin: Boolean, sessionRunning: Boolean, now: Instant): Boolean =
        requiresWait(settings, hasPin) && !sessionRunning && readyAt(settings)?.let { !now.isBefore(it) } == true
}
