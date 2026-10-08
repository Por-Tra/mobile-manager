package com.lucas.mobilemanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priority: Int,
    @ColumnInfo(name = "auto_activation") val autoActivation: Boolean,
    val enabled: Boolean,
)

@Entity(
    tableName = "profile_schedule",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class ProfileScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    /** Bit `n` = jour ISO `n + 1` (bit 0 = lundi). */
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    /** Minutes depuis minuit, heure locale. */
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int,
)

/** Règle d'un profil, ou règle globale si [profileId] est `null`. */
@Entity(
    tableName = "rule",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profile_id")],
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long?,
    /** `ALL_APPS`, `APP` ou `ZONE`. */
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "package_name") val packageName: String?,
    val zone: String?,
    /** `ALLOW`, `LIMIT` ou `BLOCK`. */
    val action: String,
    @ColumnInfo(name = "daily_limit_minutes") val dailyLimitMinutes: Long?,
)

data class ProfileWithDetails(
    @Embedded val profile: ProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val schedules: List<ProfileScheduleEntity>,
    @Relation(parentColumn = "id", entityColumn = "profile_id")
    val rules: List<RuleEntity>,
)
