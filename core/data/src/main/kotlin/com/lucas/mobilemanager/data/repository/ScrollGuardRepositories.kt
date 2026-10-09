package com.lucas.mobilemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lucas.mobilemanager.data.db.ScrollGuardDao
import com.lucas.mobilemanager.data.db.toDomain
import com.lucas.mobilemanager.data.db.toEntity
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuard
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuardSettings
import com.lucas.mobilemanager.domain.repository.ScrollGuardRepository
import com.lucas.mobilemanager.domain.repository.ScrollGuardSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class RoomScrollGuardRepository @Inject constructor(
    private val dao: ScrollGuardDao,
) : ScrollGuardRepository {

    override fun observeGuards(): Flow<List<ScrollGuard>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(guard: ScrollGuard) = dao.upsert(guard.toEntity())

    override suspend fun delete(packageName: String) = dao.delete(packageName)
}

class DataStoreScrollGuardSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ScrollGuardSettingsRepository {

    private val defaults = ScrollGuardSettings()

    override val settings: Flow<ScrollGuardSettings> = dataStore.data.map { prefs ->
        ScrollGuardSettings(
            enabled = prefs[Keys.ENABLED] ?: defaults.enabled,
            sessionGap = prefs[Keys.GAP_SECONDS]?.let(Duration::ofSeconds) ?: defaults.sessionGap,
            pauseDuration = prefs[Keys.PAUSE_SECONDS]?.let(Duration::ofSeconds) ?: defaults.pauseDuration,
            goal = prefs[Keys.GOAL] ?: defaults.goal,
        )
    }.distinctUntilChanged()

    /** Stockés sous la forme « package|fin en ms ». */
    override val cooldowns: Flow<Map<String, Instant>> = dataStore.data.map { prefs ->
        prefs[Keys.COOLDOWNS].orEmpty().mapNotNull { entry ->
            val separator = entry.lastIndexOf('|')
            val until = entry.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
            entry.substring(0, separator) to Instant.ofEpochMilli(until)
        }.toMap()
    }.distinctUntilChanged()

    override suspend fun update(change: (ScrollGuardSettings) -> ScrollGuardSettings) {
        dataStore.edit { prefs ->
            val current = ScrollGuardSettings(
                enabled = prefs[Keys.ENABLED] ?: defaults.enabled,
                sessionGap = prefs[Keys.GAP_SECONDS]?.let(Duration::ofSeconds) ?: defaults.sessionGap,
                pauseDuration = prefs[Keys.PAUSE_SECONDS]?.let(Duration::ofSeconds) ?: defaults.pauseDuration,
                goal = prefs[Keys.GOAL] ?: defaults.goal,
            )
            val updated = change(current)
            prefs[Keys.ENABLED] = updated.enabled
            prefs[Keys.GAP_SECONDS] = updated.sessionGap.seconds
            prefs[Keys.PAUSE_SECONDS] = updated.pauseDuration.seconds
            prefs[Keys.GOAL] = updated.goal
        }
    }

    override suspend fun setCooldown(packageName: String, until: Instant) {
        dataStore.edit { prefs ->
            val others = prefs[Keys.COOLDOWNS].orEmpty().filterNot { it.startsWith("$packageName|") }
            prefs[Keys.COOLDOWNS] = others.toSet() + "$packageName|${until.toEpochMilli()}"
        }
    }

    override suspend fun clearExpiredCooldowns(now: Instant) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.COOLDOWNS].orEmpty()
            val active = current.filter { entry ->
                entry.substringAfterLast('|').toLongOrNull()?.let { it > now.toEpochMilli() } == true
            }.toSet()
            if (active.size != current.size) prefs[Keys.COOLDOWNS] = active
        }
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("scroll_guard_enabled")
        val GAP_SECONDS = longPreferencesKey("scroll_guard_gap_s")
        val PAUSE_SECONDS = longPreferencesKey("scroll_guard_pause_s")
        val GOAL = stringPreferencesKey("scroll_guard_goal")
        val COOLDOWNS = stringSetPreferencesKey("scroll_guard_cooldowns")
    }
}
