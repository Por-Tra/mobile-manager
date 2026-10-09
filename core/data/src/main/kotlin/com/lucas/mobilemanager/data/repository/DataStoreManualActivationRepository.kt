package com.lucas.mobilemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.repository.ManualActivationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** Une seule activation manuelle à la fois, stockée dans les préférences. */
class DataStoreManualActivationRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ManualActivationRepository {

    override val activation: Flow<ManualActivation?> = dataStore.data
        .map { prefs ->
            prefs[Keys.PROFILE_ID]?.let { id ->
                ManualActivation(profileId = id, until = prefs[Keys.UNTIL_MS]?.let(Instant::ofEpochMilli))
            }
        }
        .distinctUntilChanged()

    override suspend fun activate(profileId: Long, until: Instant?) {
        dataStore.edit { prefs ->
            prefs[Keys.PROFILE_ID] = profileId
            if (until != null) prefs[Keys.UNTIL_MS] = until.toEpochMilli() else prefs.remove(Keys.UNTIL_MS)
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PROFILE_ID)
            prefs.remove(Keys.UNTIL_MS)
        }
    }

    private object Keys {
        val PROFILE_ID = longPreferencesKey("manual_profile_id")
        val UNTIL_MS = longPreferencesKey("manual_until_ms")
    }
}
