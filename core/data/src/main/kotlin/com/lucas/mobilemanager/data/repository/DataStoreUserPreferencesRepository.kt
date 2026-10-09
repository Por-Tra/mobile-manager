package com.lucas.mobilemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lucas.mobilemanager.domain.model.UserPreferences
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            defaultProfilesSeeded = prefs[Keys.DEFAULT_PROFILES_SEEDED] ?: false,
            defaultAppBlocksSeeded = prefs[Keys.DEFAULT_APP_BLOCKS_SEEDED] ?: false,
            defaultZoneRulesSeeded = prefs[Keys.DEFAULT_ZONE_RULES_SEEDED] ?: false,
            screenTimeGoalMinutes = prefs[Keys.SCREEN_TIME_GOAL_MIN],
        )
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setDefaultProfilesSeeded(seeded: Boolean) {
        dataStore.edit { it[Keys.DEFAULT_PROFILES_SEEDED] = seeded }
    }

    override suspend fun setDefaultAppBlocksSeeded(seeded: Boolean) {
        dataStore.edit { it[Keys.DEFAULT_APP_BLOCKS_SEEDED] = seeded }
    }

    override suspend fun clearLegacyScrollGuardSettings() {
        dataStore.edit { prefs -> Keys.LEGACY_SCROLL_GUARD.forEach { prefs.remove(it) } }
    }

    override suspend fun setDefaultZoneRulesSeeded(seeded: Boolean) {
        dataStore.edit { it[Keys.DEFAULT_ZONE_RULES_SEEDED] = seeded }
    }

    override suspend fun setScreenTimeGoal(minutes: Int?) {
        dataStore.edit { if (minutes != null) it[Keys.SCREEN_TIME_GOAL_MIN] = minutes else it.remove(Keys.SCREEN_TIME_GOAL_MIN) }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEFAULT_PROFILES_SEEDED = booleanPreferencesKey("default_profiles_seeded")
        val DEFAULT_APP_BLOCKS_SEEDED = booleanPreferencesKey("default_app_blocks_seeded")
        val DEFAULT_ZONE_RULES_SEEDED = booleanPreferencesKey("default_zone_rules_seeded")
        val SCREEN_TIME_GOAL_MIN = intPreferencesKey("screen_time_goal_min")

        /** Anciennes clés de l'anti-doomscrolling (retiré). */
        val LEGACY_SCROLL_GUARD = listOf(
            booleanPreferencesKey("default_scroll_guards_seeded"),
            booleanPreferencesKey("scroll_guard_enabled"),
            longPreferencesKey("scroll_guard_gap_s"),
            longPreferencesKey("scroll_guard_pause_s"),
            stringPreferencesKey("scroll_guard_goal"),
            stringSetPreferencesKey("scroll_guard_cooldowns"),
        )
    }
}
