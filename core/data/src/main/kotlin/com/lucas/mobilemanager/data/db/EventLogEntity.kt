package com.lucas.mobilemanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.EventType
import java.time.Instant

@Entity(
    tableName = "event_log",
    indices = [Index("occurred_at_ms")],
)
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nom de [EventType], stocké en texte pour rester lisible et stable si l'enum est réordonnée. */
    val type: String,
    @ColumnInfo(name = "occurred_at_ms") val occurredAtMs: Long,
    @ColumnInfo(name = "package_name") val packageName: String?,
    val zone: String?,
    val details: String?,
)

fun EventLogEntity.toDomain() = EventLogEntry(
    id = id,
    type = EventType.valueOf(type),
    occurredAt = Instant.ofEpochMilli(occurredAtMs),
    packageName = packageName,
    zone = zone,
    details = details,
)

fun EventLogEntry.toEntity() = EventLogEntity(
    id = id,
    type = type.name,
    occurredAtMs = occurredAt.toEpochMilli(),
    packageName = packageName,
    zone = zone,
    details = details,
)
