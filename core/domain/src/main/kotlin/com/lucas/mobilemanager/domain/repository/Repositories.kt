package com.lucas.mobilemanager.domain.repository

import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.model.UserPreferences
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.zones.ZoneKey
import com.lucas.mobilemanager.domain.zones.ZoneUsageDelta
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

interface EventLogRepository {
    suspend fun record(entry: EventLogEntry)

    /** Événements dans l'intervalle [from, to), du plus ancien au plus récent. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<EventLogEntry>>
}

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun setDefaultProfilesSeeded(seeded: Boolean)

    suspend fun setDefaultAppBlocksSeeded(seeded: Boolean)

    /** Efface les réglages de l'anti-doomscrolling retiré (réglages, temps de repos). */
    suspend fun clearLegacyScrollGuardSettings()

    suspend fun setDefaultZoneRulesSeeded(seeded: Boolean)

    suspend fun setScreenTimeGoal(minutes: Int?)
}

interface StrictSessionRepository {
    /** Session non close (en cours, ou heure de fin passée mais pas encore clôturée), sinon `null`. */
    fun observeCurrent(): Flow<StrictSession?>

    suspend fun current(): StrictSession?

    /** Enregistre une nouvelle session et renvoie son id. */
    suspend fun insert(session: StrictSession): Long

    suspend fun setOverrideRequestedAt(sessionId: Long, at: Instant?)

    suspend fun end(sessionId: Long, endedAt: Instant, reason: StrictSession.EndReason)

    /** Sessions commencées dans [from, to), pour le tableau de bord. */
    suspend fun sessionsStartedBetween(from: Instant, to: Instant): List<StrictSession>
}

/**
 * Temps d'écran par jour et par app, figé chaque jour : Android ne garde les événements détaillés
 * qu'environ une semaine, ces instantanés permettent de comparer deux semaines.
 */
interface DailyUsageRepository {
    /** Jours déjà enregistrés dans [from, to] (inclus). */
    suspend fun storedDays(from: LocalDate, to: LocalDate): Set<LocalDate>

    /** Temps par app pour chaque jour enregistré de [from, to] (un jour sans usage = map vide). */
    suspend fun usageBetween(from: LocalDate, to: LocalDate): Map<LocalDate, Map<String, Duration>>

    suspend fun store(date: LocalDate, usage: Map<String, Duration>)
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

/** Temps passé dans les zones (Reels, Shorts…) par jour local : quotas et tableau de bord. */
interface ZoneUsageRepository {
    suspend fun add(deltas: List<ZoneUsageDelta>)

    /** Temps enregistré dans chaque zone le jour [date]. */
    fun observeDay(date: LocalDate): Flow<Map<ZoneKey, Duration>>

    suspend fun usageOn(date: LocalDate): Map<ZoneKey, Duration>

    /** Temps par zone pour chaque jour de [from, to] (inclus) qui en a. */
    suspend fun usageBetween(from: LocalDate, to: LocalDate): Map<LocalDate, Map<ZoneKey, Duration>>
}
