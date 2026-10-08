package com.lucas.mobilemanager.domain.repository

import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.UserPreferences
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
}
