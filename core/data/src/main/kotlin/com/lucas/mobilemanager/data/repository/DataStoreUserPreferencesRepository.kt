package com.lucas.mobilemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
            defaultScrollGuardsSeeded = prefs[Keys.DEFAULT_SCROLL_GUARDS_SEEDED] ?: false,
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

    override suspend fun setDefaultScrollGuardsSeeded(seeded: Boolean) {
        dataStore.edit { it[Keys.DEFAULT_SCROLL_GUARDS_SEEDED] = seeded }
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
        val DEFAULT_SCROLL_GUARDS_SEEDED = booleanPreferencesKey("default_scroll_guards_seeded")
        val DEFAULT_ZONE_RULES_SEEDED = booleanPreferencesKey("default_zone_rules_seeded")
        val SCREEN_TIME_GOAL_MIN = intPreferencesKey("screen_time_goal_min")
    }
}
