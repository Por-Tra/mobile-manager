package com.lucas.mobilemanager.data.repository

import com.lucas.mobilemanager.data.db.StrictSessionDao
import com.lucas.mobilemanager.data.db.toDomain
import com.lucas.mobilemanager.data.db.toEntity
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomStrictSessionRepository @Inject constructor(
    private val dao: StrictSessionDao,
) : StrictSessionRepository {

    override fun observeCurrent(): Flow<StrictSession?> = dao.observeCurrent().map { it?.toDomain() }

    override suspend fun current(): StrictSession? = dao.current()?.toDomain()

    override suspend fun insert(session: StrictSession): Long = dao.insert(session.toEntity())

    override suspend fun setOverrideRequestedAt(sessionId: Long, at: Instant?) =
        dao.setOverrideRequestedAt(sessionId, at?.toEpochMilli())

    override suspend fun end(sessionId: Long, endedAt: Instant, reason: StrictSession.EndReason) =
        dao.end(sessionId, endedAt.toEpochMilli(), reason.name)
}
