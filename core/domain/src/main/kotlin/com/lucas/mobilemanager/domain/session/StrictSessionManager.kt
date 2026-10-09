package com.lucas.mobilemanager.domain.session

import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.EventType
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.repository.EventLogRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy.OverrideState
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy.ValidationError
import com.lucas.mobilemanager.domain.time.TimeProvider
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Cycle de vie d'une session de concentration : démarrage, dérogation, fin. */
class StrictSessionManager @Inject constructor(
    private val repository: StrictSessionRepository,
    private val eventLog: EventLogRepository,
    private val timeProvider: TimeProvider,
) {
    sealed interface StartResult {
        data class Started(val session: StrictSession) : StartResult
        data class Invalid(val errors: Set<ValidationError>) : StartResult
        data object AlreadyRunning : StartResult
    }

    suspend fun start(endsAt: Instant, blockedPackages: Set<String>, reflectionDelay: Duration): StartResult {
        val now = timeProvider.now()
        completeIfDue()
        if (repository.current()?.isRunning(now) == true) return StartResult.AlreadyRunning

        val errors = StrictSessionPolicy.validateNew(now, endsAt, blockedPackages, reflectionDelay)
        if (errors.isNotEmpty()) return StartResult.Invalid(errors)

        val session = StrictSession(
            startedAt = now,
            endsAt = endsAt,
            blockedPackages = blockedPackages,
            reflectionDelay = reflectionDelay,
        )
        val id = repository.insert(session)
        log(EventType.STRICT_SESSION_STARTED, now, details = "${blockedPackages.size} apps")
        return StartResult.Started(session.copy(id = id))
    }

    /** Lance (ou relance, si la précédente a expiré) le délai de réflexion. */
    suspend fun requestOverride() {
        val now = timeProvider.now()
        val session = runningSession(now) ?: return
        when (StrictSessionPolicy.overrideState(session, now)) {
            OverrideState.NotRequested, OverrideState.Expired ->
                repository.setOverrideRequestedAt(session.id, now)
            is OverrideState.Waiting, is OverrideState.Available -> Unit
        }
    }

    /** « Finalement, je continue » : annule la demande en cours. */
    suspend fun cancelOverride() {
        val session = runningSession(timeProvider.now()) ?: return
        repository.setOverrideRequestedAt(session.id, null)
    }

    /** Termine la session, uniquement si la fenêtre de dérogation est ouverte. */
    suspend fun endByOverride(): Boolean {
        val now = timeProvider.now()
        val session = runningSession(now) ?: return false
        if (StrictSessionPolicy.overrideState(session, now) !is OverrideState.Available) return false
        repository.end(session.id, now, StrictSession.EndReason.OVERRIDDEN)
        log(EventType.STRICT_SESSION_OVERRIDDEN, now)
        return true
    }

    /** Clôt la session si son heure de fin est passée. Renvoie vrai si une session a été close. */
    suspend fun completeIfDue(): Boolean {
        val now = timeProvider.now()
        val session = repository.current() ?: return false
        if (now.isBefore(session.endsAt)) return false
        repository.end(session.id, session.endsAt, StrictSession.EndReason.COMPLETED)
        log(EventType.STRICT_SESSION_ENDED, session.endsAt)
        return true
    }

    suspend fun recordProtectionDisabled(details: String) {
        log(EventType.PROTECTION_DISABLED, timeProvider.now(), details)
    }

    private suspend fun runningSession(now: Instant): StrictSession? =
        repository.current()?.takeIf { it.isRunning(now) }

    private suspend fun log(type: EventType, at: Instant, details: String? = null) {
        eventLog.record(EventLogEntry(type = type, occurredAt = at, details = details))
    }
}
