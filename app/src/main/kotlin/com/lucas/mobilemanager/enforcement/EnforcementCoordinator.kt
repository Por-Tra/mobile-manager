package com.lucas.mobilemanager.enforcement

import android.util.Log
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.doomscroll.GuardDecision
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuard
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuardPolicy
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuardSettings
import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.EventType
import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.repository.EventLogRepository
import com.lucas.mobilemanager.domain.repository.ManualActivationRepository
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.ScrollGuardRepository
import com.lucas.mobilemanager.domain.repository.ScrollGuardSettingsRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.rules.AppRequest
import com.lucas.mobilemanager.domain.rules.BlockReason
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.rules.ProfileResolver
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleEngine
import com.lucas.mobilemanager.domain.time.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Relie le système (app au premier plan, usage) au moteur de règles du domaine.
 * Ne connaît pas le service d'accessibilité : il décide, le service applique.
 */
@Singleton
class EnforcementCoordinator @Inject constructor(
    profileRepository: ProfileRepository,
    strictSessionRepository: StrictSessionRepository,
    manualActivationRepository: ManualActivationRepository,
    scrollGuardRepository: ScrollGuardRepository,
    private val scrollGuardSettingsRepository: ScrollGuardSettingsRepository,
    @ApplicationScope appScope: CoroutineScope,
    private val profileResolver: ProfileResolver,
    private val ruleEngine: RuleEngine,
    private val timeProvider: TimeProvider,
    private val usageStatsReader: UsageStatsReader,
    private val whitelistProvider: SystemWhitelistProvider,
    private val eventLogRepository: EventLogRepository,
) {
    private data class RulesSnapshot(
        val profiles: List<Profile>,
        val globalRules: List<Rule>,
        val manualActivation: ManualActivation?,
    )

    /** Profils, règles et activation manuelle gardés en mémoire : une évaluation ne relit pas la base. */
    private val rules = combine(
        profileRepository.observeProfiles(),
        profileRepository.observeGlobalRules(),
        manualActivationRepository.activation,
        ::RulesSnapshot,
    ).stateIn(appScope, SharingStarted.Eagerly, initialValue = null)

    private data class GuardSnapshot(
        val settings: ScrollGuardSettings,
        val guards: Map<String, ScrollGuard>,
        val cooldowns: Map<String, Instant>,
    )

    /** Réglages anti-doomscrolling gardés en mémoire. */
    private val guards = combine(
        scrollGuardSettingsRepository.settings,
        scrollGuardRepository.observeGuards(),
        scrollGuardSettingsRepository.cooldowns,
    ) { settings, guards, cooldowns -> GuardSnapshot(settings, guards.associateBy { it.packageName }, cooldowns) }
        .stateIn(appScope, SharingStarted.Eagerly, initialValue = null)

    /** Dernier écran de pause affiché, par app (en mémoire : une pause manquée après un redémarrage est sans gravité). */
    private val lastPauseAt = ConcurrentHashMap<String, Instant>()

    /** Enveloppe pour distinguer « pas encore chargée » (`null`) de « aucune session ». */
    private data class SessionSnapshot(val session: StrictSession?)

    /** Session de concentration non close (peut être arrivée à son heure de fin). */
    private val currentSession: StateFlow<SessionSnapshot?> = strictSessionRepository.observeCurrent()
        .map(::SessionSnapshot)
        .stateIn(appScope, SharingStarted.Eagerly, initialValue = null)

    /** Session qui bloque en ce moment, lue en mémoire (appel synchrone, sans accès disque). */
    fun runningSession(): StrictSession? =
        currentSession.value?.session?.takeIf { it.isRunning(timeProvider.now()) }

    fun isWhitelisted(packageName: String): Boolean = packageName in whitelistProvider.current()

    suspend fun evaluate(packageName: String): Decision {
        val whitelist = whitelistProvider.current()
        if (packageName in whitelist) return Decision.Allowed(remainingToday = null)

        val snapshot = rules.filterNotNull().first()
        currentSession.filterNotNull().first() // attend le premier chargement après un démarrage
        val now = timeProvider.localNow()
        val policy = profileResolver.resolve(
            profiles = snapshot.profiles,
            globalRules = snapshot.globalRules,
            manualActivation = snapshot.manualActivation,
            strictSession = runningSession(),
            now = now,
        )
        val request = AppRequest(packageName, usageToday = usageStatsReader.usageToday(packageName))
        val engineDecision = ruleEngine.evaluate(policy, request, whitelist, now)
        return applyScrollGuard(packageName, engineDecision, request.usageToday).also {
            if (BuildConfig.DEBUG) Log.d(TAG, "$packageName usage=${request.usageToday} -> $it")
        }
    }

    /** Anti-doomscrolling, appliqué après le moteur : n'ajoute que des restrictions. */
    private suspend fun applyScrollGuard(packageName: String, engine: Decision, usageToday: Duration): Decision {
        if (engine !is Decision.Allowed) return engine
        val snapshot = guards.filterNotNull().first()
        if (!snapshot.settings.enabled) return engine
        val guard = snapshot.guards[packageName] ?: return engine
        val now = timeProvider.now()
        val session = usageStatsReader.continuousSession(packageName, snapshot.settings.sessionGap)
        val cooldownUntil = snapshot.cooldowns[packageName]
        val guardDecision = ScrollGuardPolicy.evaluate(
            guard = guard,
            session = session,
            usageToday = usageToday,
            lastPauseAt = lastPauseAt[packageName],
            cooldownUntil = cooldownUntil,
            now = now,
            zone = timeProvider.zone(),
        )
        // Nouveau temps de repos : on le mémorise pour qu'il survive à un redémarrage.
        if (guardDecision is GuardDecision.Blocked &&
            guardDecision.reason is BlockReason.SessionLimitReached &&
            (cooldownUntil == null || !now.isBefore(cooldownUntil))
        ) {
            scrollGuardSettingsRepository.setCooldown(packageName, guardDecision.until)
        }
        return ScrollGuardPolicy.combine(engine, guardDecision)
    }

    /** Durée de l'écran de pause et objectif à rappeler. */
    suspend fun pauseSettings(): ScrollGuardSettings = guards.filterNotNull().first().settings

    suspend fun recordPause(packageName: String, sessionLength: Duration) {
        val now = timeProvider.now()
        lastPauseAt[packageName] = now
        eventLogRepository.record(
            EventLogEntry(
                type = EventType.INTERSTITIAL_SHOWN,
                occurredAt = now,
                packageName = packageName,
                details = "session:${sessionLength.toMinutes()}",
            ),
        )
    }

    suspend fun recordBlock(packageName: String, decision: Decision.Blocked) {
        eventLogRepository.record(
            EventLogEntry(
                type = EventType.APP_BLOCKED,
                occurredAt = timeProvider.now(),
                packageName = packageName,
                details = decision.reason.logLabel(),
            ),
        )
    }

    private companion object {
        const val TAG = "MobileManager"
    }

    private fun BlockReason.logLabel(): String = when (this) {
        BlockReason.StrictSession -> "strict_session"
        is BlockReason.Profile -> "profile:$profileId"
        BlockReason.GlobalRule -> "global_rule"
        is BlockReason.DailyLimitReached -> "daily_limit:${limit.toMinutes()}"
        is BlockReason.SessionLimitReached -> "session_limit:${limit.toMinutes()}"
    }
}
