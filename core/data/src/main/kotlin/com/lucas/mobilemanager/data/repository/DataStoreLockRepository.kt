package com.lucas.mobilemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucas.mobilemanager.domain.lock.LockConfig
import com.lucas.mobilemanager.domain.lock.PinHasher
import com.lucas.mobilemanager.domain.repository.LockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/** Le code n'est jamais stocké : seulement son empreinte et son sel (voir `PinHasher`). */
class DataStoreLockRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LockRepository {

    override val config: Flow<LockConfig?> = dataStore.data.map { prefs ->
        val hash = prefs[Keys.HASH] ?: return@map null
        val salt = prefs[Keys.SALT] ?: return@map null
        LockConfig(
            pinHash = hash,
            salt = salt,
            iterations = prefs[Keys.ITERATIONS] ?: PinHasher.LEGACY_ITERATIONS,
            failures = prefs[Keys.FAILURES] ?: 0,
            lockedOutUntil = prefs[Keys.LOCKED_OUT_UNTIL]?.let(Instant::ofEpochMilli),
        )
    }.distinctUntilChanged()

    override suspend fun save(config: LockConfig?) {
        dataStore.edit { prefs ->
            if (config == null) {
                listOf(Keys.HASH, Keys.SALT, Keys.ITERATIONS, Keys.FAILURES, Keys.LOCKED_OUT_UNTIL).forEach { prefs.remove(it) }
                return@edit
            }
            prefs[Keys.HASH] = config.pinHash
            prefs[Keys.SALT] = config.salt
            prefs[Keys.ITERATIONS] = config.iterations
            prefs[Keys.FAILURES] = config.failures
            config.lockedOutUntil?.let { prefs[Keys.LOCKED_OUT_UNTIL] = it.toEpochMilli() } ?: prefs.remove(Keys.LOCKED_OUT_UNTIL)
        }
    }

    private object Keys {
        val HASH = stringPreferencesKey("lock_pin_hash")
        val SALT = stringPreferencesKey("lock_pin_salt")
        val ITERATIONS = intPreferencesKey("lock_pin_iterations")
        val FAILURES = intPreferencesKey("lock_failures")
        val LOCKED_OUT_UNTIL = longPreferencesKey("lock_locked_out_until")
    }
}
