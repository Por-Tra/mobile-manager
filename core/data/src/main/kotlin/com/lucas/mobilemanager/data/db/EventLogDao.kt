package com.lucas.mobilemanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Insert
    suspend fun insert(entity: EventLogEntity): Long

    @Query(
        "SELECT * FROM event_log WHERE occurred_at_ms >= :fromMs AND occurred_at_ms < :toMs " +
            "ORDER BY occurred_at_ms ASC",
    )
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<EventLogEntity>>
}
