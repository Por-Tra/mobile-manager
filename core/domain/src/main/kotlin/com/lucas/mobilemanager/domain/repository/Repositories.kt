package com.lucas.mobilemanager.domain.repository

import com.lucas.mobilemanager.domain.doomscroll.ScrollGuard
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuardSettings
import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.model.UserPreferences
import com.lucas.mobilemanager.domain.rules.Rule
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface EventLogRepository {
    suspend fun record(entry: EventLogEntry)

    /** Événements dans l'intervalle [from, to), du plus ancien au plus récent. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<EventLogEntry>>
}

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setDefaultProfilesSeeded(seeded: Boolean)

    suspend fun setDefaultScrollGuardsSeeded(seeded: Boolean)

    suspend fun setDefaultZoneRulesSeeded(seeded: Boolean)
}

interface StrictSessionRepository {
    /** Session non close (en cours, ou heure de fin passée mais pas encore clôturée), sinon `null`. */
    fun observeCurrent(): Flow<StrictSession?>

    suspend fun current(): StrictSession?

    /** Enregistre une nouvelle session et renvoie son id. */
    suspend fun insert(session: StrictSession): Long

    suspend fun setOverrideRequestedAt(sessionId: Long, at: Instant?)

    suspend fun end(sessionId: Long, endedAt: Instant, reason: StrictSession.EndReason)
}

interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>

    /** Règles globales par défaut (niveau le moins prioritaire). */
    fun observeGlobalRules(): Flow<List<Rule>>

    /** Crée le profil si `id == 0`, sinon le remplace entièrement (plages et règles). Renvoie l'id. */
    suspend fun upsert(profile: Profile): Long

    suspend fun delete(profileId: Long)

    suspend fun setEnabled(profileId: Long, enabled: Boolean)

    suspend fun replaceGlobalRules(rules: List<Rule>)
}

/** Profil activé à la main (un seul à la fois). */
interface ManualActivationRepository {
    val activation: Flow<ManualActivation?>

    suspend fun activate(profileId: Long, until: Instant?)

    suspend fun clear()
}

/** Réglages anti-doomscrolling par app. */
interface ScrollGuardRepository {
    fun observeGuards(): Flow<List<ScrollGuard>>

    suspend fun upsert(guard: ScrollGuard)

    suspend fun delete(packageName: String)
}

/** Réglages généraux anti-doomscrolling et temps de repos en cours. */
interface ScrollGuardSettingsRepository {
    val settings: Flow<ScrollGuardSettings>

    /** Fin du temps de repos en cours, par package. */
    val cooldowns: Flow<Map<String, Instant>>

    suspend fun update(change: (ScrollGuardSettings) -> ScrollGuardSettings)

    suspend fun setCooldown(packageName: String, until: Instant)

    /** Retire les temps de repos terminés. */
    suspend fun clearExpiredCooldowns(now: Instant)
}
