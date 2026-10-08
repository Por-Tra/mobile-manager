package com.lucas.mobilemanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Transaction
    @Query("SELECT * FROM profile ORDER BY priority DESC, id ASC")
    fun observeProfiles(): Flow<List<ProfileWithDetails>>

    @Query("SELECT * FROM rule WHERE profile_id IS NULL ORDER BY id ASC")
    fun observeGlobalRules(): Flow<List<RuleEntity>>

    @Insert
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Insert
    suspend fun insertSchedules(schedules: List<ProfileScheduleEntity>)

    @Insert
    suspend fun insertRules(rules: List<RuleEntity>)

    @Query("DELETE FROM profile_schedule WHERE profile_id = :profileId")
    suspend fun deleteSchedules(profileId: Long)

    @Query("DELETE FROM rule WHERE profile_id = :profileId")
    suspend fun deleteRules(profileId: Long)

    @Query("DELETE FROM rule WHERE profile_id IS NULL")
    suspend fun deleteGlobalRules()

    @Query("DELETE FROM profile WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    /**
     * Crée ou remplace entièrement un profil (plages et règles comprises).
     * Le `profileId` des plages et règles fournies est remplacé par l'id du profil.
     */
    @Transaction
    suspend fun upsert(
        profile: ProfileEntity,
        schedules: List<ProfileScheduleEntity>,
        rules: List<RuleEntity>,
    ): Long {
        val id = if (profile.id == 0L) {
            insertProfile(profile)
        } else {
            updateProfile(profile)
            deleteSchedules(profile.id)
            deleteRules(profile.id)
            profile.id
        }
        insertSchedules(schedules.map { it.copy(id = 0, profileId = id) })
        insertRules(rules.map { it.copy(id = 0, profileId = id) })
        return id
    }

    @Transaction
    suspend fun replaceGlobalRules(rules: List<RuleEntity>) {
        deleteGlobalRules()
        insertRules(rules)
    }
}
