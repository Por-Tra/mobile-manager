package com.lucas.mobilemanager.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventLogEntity::class,
        ProfileEntity::class,
        ProfileScheduleEntity::class,
        RuleEntity::class,
        StrictSessionEntity::class,
        ScrollGuardEntity::class,
        DailyUsageEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // v2 : ajout des tables profile, profile_schedule et rule.
        AutoMigration(from = 1, to = 2),
        // v3 : ajout de la table strict_session.
        AutoMigration(from = 2, to = 3),
        // v4 : ajout de la table scroll_guard (anti-doomscrolling).
        AutoMigration(from = 3, to = 4),
        // v5 : ajout de la table daily_usage (instantanés quotidiens du tableau de bord).
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class MobileManagerDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
    abstract fun profileDao(): ProfileDao
    abstract fun strictSessionDao(): StrictSessionDao
    abstract fun scrollGuardDao(): ScrollGuardDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        const val NAME = "mobile_manager.db"
    }
}
