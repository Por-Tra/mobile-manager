package com.lucas.mobilemanager.enforcement

import android.os.SystemClock
import android.util.Log
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.EventType
import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.repository.EventLogRepository
import com.lucas.mobilemanager.domain.repository.ManualActivationRepository
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.rules.AppRequest
import com.lucas.mobilemanager.domain.rules.BlockReason
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.rules.ProfileResolver
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleEngine
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.zones.ZoneUsageRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
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
    @ApplicationScope appScope: CoroutineScope,
    private val profileResolver: ProfileResolver,
    private val ruleEngine: RuleEngine,
    private val timeProvider: TimeProvider,
    private val usageStatsReader: UsageStatsReader,
    private val whitelistProvider: SystemWhitelistProvider,
    private val eventLogRepository: EventLogRepository,
    private val zoneUsageRecorder: ZoneUsageRecorder,
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

    /** Dernier temps d'usage lu pour chaque app et quand (millisecondes de `elapsedRealtime`). */
    private val lastAppUsage = ConcurrentHashMap<String, Pair<Duration, Long>>()

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
        if (packageName in whitelistProvider.current()) return Decision.Allowed(remainingToday = null)
        val usage = usageStatsReader.usageToday(packageName)
        lastAppUsage[packageName] = usage to SystemClock.elapsedRealtime()
        return engineDecision(AppRequest(packageName, usageToday = usage)).also {
            if (BuildConfig.DEBUG) Log.d(TAG, "$packageName usage=$usage -> $it")
        }
    }

    /**
     * Décision du moteur pour une **zone** de l'app (Reels, Shorts…) : un quota posé sur la zone
     * se mesure au temps passé dans la zone aujourd'hui.
     */
    suspend fun evaluateZone(packageName: String, zone: String): Decision {
        if (packageName in whitelistProvider.current()) return Decision.Allowed(remainingToday = null)
        val zoneUsage = zoneUsageRecorder.usageToday(packageName, zone)
        val request = AppRequest(packageName, zone = zone, usageToday = appUsageForZone(packageName), zoneUsageToday = zoneUsage)
        return engineDecision(request).also {
            if (BuildConfig.DEBUG) Log.d(TAG, "$packageName zone=$zone zoneUsage=$zoneUsage -> $it")
        }
    }

    /**
     * Usage de l'app pour une requête de zone, sans relire les statistiques d'Android à chaque
     * vérification : la dernière lecture (≤ 30 s, faite par la surveillance de l'app) plus le
     * temps écoulé depuis, l'app étant restée au premier plan.
     */
    private fun appUsageForZone(packageName: String): Duration {
        val (usage, atMs) = lastAppUsage[packageName] ?: return usageStatsReader.usageToday(packageName)
        val elapsed = SystemClock.elapsedRealtime() - atMs
        return if (elapsed > APP_USAGE_REUSE_MS) usageStatsReader.usageToday(packageName) else usage + Duration.ofMillis(elapsed)
    }

    private suspend fun engineDecision(request: AppRequest): Decision {
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
        return ruleEngine.evaluate(policy, request, whitelistProvider.current(), now)
    }

    suspend fun recordZoneRedirect(packageName: String, zone: String) {
        eventLogRepository.record(
            EventLogEntry(type = EventType.ZONE_REDIRECTED, occurredAt = timeProvider.now(), packageName = packageName, zone = zone),
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
        const val APP_USAGE_REUSE_MS = 30_000L
    }

    private fun BlockReason.logLabel(): String = when (this) {
        BlockReason.StrictSession -> "strict_session"
        is BlockReason.Profile -> "profile:$profileId"
        BlockReason.GlobalRule -> "global_rule"
        is BlockReason.DailyLimitReached -> "daily_limit:${limit.toMinutes()}"
    }
}
