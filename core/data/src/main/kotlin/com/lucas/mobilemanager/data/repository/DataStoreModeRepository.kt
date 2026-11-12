package com.lucas.mobilemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucas.mobilemanager.domain.mode.AppMode
import com.lucas.mobilemanager.domain.mode.ModeSettings
import com.lucas.mobilemanager.domain.repository.ModeRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class DataStoreModeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ModeRepository {
    override val settings = dataStore.data.map(::decode)

    override suspend fun update(transform: (ModeSettings) -> ModeSettings) {
        dataStore.edit { prefs ->
            val next = transform(decode(prefs))
            val mode = next.selectedMode
            if (mode == null) prefs.remove(MODE) else prefs[MODE] = mode.name
            prefs[WAIT] = next.minimumWaitMinutes
            val requestedAt = next.unlockRequestedAt
            if (requestedAt == null) prefs.remove(REQUESTED_AT) else prefs[REQUESTED_AT] = requestedAt.toEpochMilli()
        }
    }

    private fun decode(prefs: Preferences) = ModeSettings(
        selectedMode = AppMode.entries.firstOrNull { it.name == prefs[MODE] },
        minimumWaitMinutes = (prefs[WAIT] ?: 60).coerceIn(1, 1440),
        unlockRequestedAt = prefs[REQUESTED_AT]?.let(Instant::ofEpochMilli),
    )

    private companion object {
        val MODE = stringPreferencesKey("app_mode")
        val WAIT = intPreferencesKey("mode_minimum_wait_minutes")
        val REQUESTED_AT = longPreferencesKey("mode_unlock_requested_at")
    }
}
