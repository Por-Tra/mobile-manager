package com.lucas.mobilemanager.data.repository

import com.lucas.mobilemanager.data.db.EventLogDao
import com.lucas.mobilemanager.data.db.toDomain
import com.lucas.mobilemanager.data.db.toEntity
import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.repository.EventLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomEventLogRepository @Inject constructor(
    private val dao: EventLogDao,
) : EventLogRepository {

    override suspend fun record(entry: EventLogEntry) {
        dao.insert(entry.toEntity())
    }

    override fun observeBetween(from: Instant, to: Instant): Flow<List<EventLogEntry>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli())
            .map { entities -> entities.map { it.toDomain() } }
}
