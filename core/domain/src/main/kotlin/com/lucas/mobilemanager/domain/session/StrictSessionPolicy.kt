package com.lucas.mobilemanager.domain.session

import com.lucas.mobilemanager.domain.model.StrictSession
import java.time.Duration
import java.time.Instant

/** Règles des sessions de concentration : validation et parcours de dérogation. */
object StrictSessionPolicy {

    val MAX_DURATION: Duration = Duration.ofHours(12)
    val MIN_REFLECTION_DELAY: Duration = Duration.ofMinutes(2)
    val MAX_REFLECTION_DELAY: Duration = Duration.ofMinutes(10)

    /**
     * Une fois le délai écoulé, la fin anticipée n'est possible que pendant cette fenêtre.
     * Sans elle, on pourrait lancer le délai dès le début de la session et sortir à tout moment.
     */
    val OVERRIDE_WINDOW: Duration = Duration.ofMinutes(2)

    enum class ValidationError { END_NOT_IN_FUTURE, TOO_LONG, NO_APPS, DELAY_OUT_OF_RANGE }

    fun validateNew(
        start: Instant,
        end: Instant,
        blockedPackages: Set<String>,
        reflectionDelay: Duration,
    ): Set<ValidationError> = buildSet {
        if (!end.isAfter(start)) add(ValidationError.END_NOT_IN_FUTURE)
        if (Duration.between(start, end) > MAX_DURATION) add(ValidationError.TOO_LONG)
        if (blockedPackages.isEmpty()) add(ValidationError.NO_APPS)
        if (reflectionDelay < MIN_REFLECTION_DELAY || reflectionDelay > MAX_REFLECTION_DELAY) {
            add(ValidationError.DELAY_OUT_OF_RANGE)
        }
    }

    sealed interface OverrideState {
        /** Aucune demande en cours. */
        data object NotRequested : OverrideState

        /** Délai de réflexion en cours. */
        data class Waiting(val availableAt: Instant) : OverrideState

        /** La fin anticipée est possible jusqu'à [expiresAt]. */
        data class Available(val expiresAt: Instant) : OverrideState

        /** La fenêtre est passée sans confirmation : il faut refaire une demande. */
        data object Expired : OverrideState
    }

    fun overrideState(session: StrictSession, now: Instant): OverrideState {
        val requestedAt = session.overrideRequestedAt ?: return OverrideState.NotRequested
        val availableAt = requestedAt + session.reflectionDelay
        val expiresAt = availableAt + OVERRIDE_WINDOW
        return when {
            now.isBefore(availableAt) -> OverrideState.Waiting(availableAt)
            now.isBefore(expiresAt) -> OverrideState.Available(expiresAt)
            else -> OverrideState.Expired
        }
    }
}
