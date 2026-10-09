package com.lucas.mobilemanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.lucas.mobilemanager.domain.model.StrictSession
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/** Sessions de concentration, en cours et passées (historique pour le tableau de bord). */
@Entity(tableName = "strict_session", indices = [Index("ended_at_ms")])
data class StrictSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "ends_at_ms") val endsAtMs: Long,
    /** Packages bloqués, un par ligne. */
    @ColumnInfo(name = "blocked_packages") val blockedPackages: String,
    @ColumnInfo(name = "reflection_delay_s") val reflectionDelaySeconds: Long,
    @ColumnInfo(name = "override_requested_at_ms") val overrideRequestedAtMs: Long?,
    @ColumnInfo(name = "ended_at_ms") val endedAtMs: Long?,
    /** Nom de [StrictSession.EndReason]. */
    @ColumnInfo(name = "end_reason") val endReason: String?,
)

@Dao
interface StrictSessionDao {
    @Query("SELECT * FROM strict_session WHERE ended_at_ms IS NULL ORDER BY id DESC LIMIT 1")
    fun observeCurrent(): Flow<StrictSessionEntity?>

    @Query("SELECT * FROM strict_session WHERE ended_at_ms IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun current(): StrictSessionEntity?

    @Insert
    suspend fun insert(entity: StrictSessionEntity): Long

    @Query("UPDATE strict_session SET override_requested_at_ms = :atMs WHERE id = :id")
    suspend fun setOverrideRequestedAt(id: Long, atMs: Long?)

    @Query("UPDATE strict_session SET ended_at_ms = :endedAtMs, end_reason = :reason WHERE id = :id")
    suspend fun end(id: Long, endedAtMs: Long, reason: String)
}

fun StrictSessionEntity.toDomain() = StrictSession(
    id = id,
    startedAt = Instant.ofEpochMilli(startedAtMs),
    endsAt = Instant.ofEpochMilli(endsAtMs),
    blockedPackages = blockedPackages.split('\n').filter { it.isNotBlank() }.toSet(),
    reflectionDelay = Duration.ofSeconds(reflectionDelaySeconds),
    overrideRequestedAt = overrideRequestedAtMs?.let(Instant::ofEpochMilli),
    endedAt = endedAtMs?.let(Instant::ofEpochMilli),
    endReason = endReason?.let(StrictSession.EndReason::valueOf),
)

fun StrictSession.toEntity() = StrictSessionEntity(
    id = id,
    startedAtMs = startedAt.toEpochMilli(),
    endsAtMs = endsAt.toEpochMilli(),
    blockedPackages = blockedPackages.sorted().joinToString("\n"),
    reflectionDelaySeconds = reflectionDelay.seconds,
    overrideRequestedAtMs = overrideRequestedAt?.toEpochMilli(),
    endedAtMs = endedAt?.toEpochMilli(),
    endReason = endReason?.name,
)
