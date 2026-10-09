package com.lucas.mobilemanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuard
import kotlinx.coroutines.flow.Flow
import java.time.Duration

/** Réglage anti-doomscrolling d'une app (durées en minutes, `null` = désactivé). */
@Entity(tableName = "scroll_guard")
data class ScrollGuardEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "pause_every_min") val pauseEveryMinutes: Long?,
    @ColumnInfo(name = "session_limit_min") val sessionLimitMinutes: Long?,
    @ColumnInfo(name = "cooldown_min") val cooldownMinutes: Long,
    @ColumnInfo(name = "daily_limit_min") val dailyLimitMinutes: Long?,
)

@Dao
interface ScrollGuardDao {
    @Query("SELECT * FROM scroll_guard ORDER BY package_name")
    fun observeAll(): Flow<List<ScrollGuardEntity>>

    @Upsert
    suspend fun upsert(entity: ScrollGuardEntity)

    @Query("DELETE FROM scroll_guard WHERE package_name = :packageName")
    suspend fun delete(packageName: String)
}

fun ScrollGuardEntity.toDomain() = ScrollGuard(
    packageName = packageName,
    pauseEvery = pauseEveryMinutes?.let(Duration::ofMinutes),
    sessionLimit = sessionLimitMinutes?.let(Duration::ofMinutes),
    cooldown = Duration.ofMinutes(cooldownMinutes),
    dailyLimit = dailyLimitMinutes?.let(Duration::ofMinutes),
)

fun ScrollGuard.toEntity() = ScrollGuardEntity(
    packageName = packageName,
    pauseEveryMinutes = pauseEvery?.toMinutes(),
    sessionLimitMinutes = sessionLimit?.toMinutes(),
    cooldownMinutes = cooldown.toMinutes(),
    dailyLimitMinutes = dailyLimit?.toMinutes(),
)
