package com.lucas.mobilemanager.data.repository

import com.lucas.mobilemanager.data.db.ProfileDao
import com.lucas.mobilemanager.data.db.toDomain
import com.lucas.mobilemanager.data.db.toEntity
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.rules.Rule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomProfileRepository @Inject constructor(
    private val dao: ProfileDao,
) : ProfileRepository {

    override fun observeProfiles(): Flow<List<Profile>> =
        dao.observeProfiles().map { list -> list.map { it.toDomain() } }

    override fun observeGlobalRules(): Flow<List<Rule>> =
        dao.observeGlobalRules().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(profile: Profile): Long =
        dao.upsert(
            profile = profile.toEntity(),
            schedules = profile.schedules.map { it.toEntity(profile.id) },
            rules = profile.rules.map { it.toEntity(profile.id) },
        )

    override suspend fun delete(profileId: Long) = dao.deleteProfile(profileId)

    override suspend fun replaceGlobalRules(rules: List<Rule>) =
        dao.replaceGlobalRules(rules.map { it.toEntity(profileId = null) })
}
