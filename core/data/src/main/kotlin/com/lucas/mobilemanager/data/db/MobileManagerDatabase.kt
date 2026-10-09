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
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // v2 : ajout des tables profile, profile_schedule et rule.
        AutoMigration(from = 1, to = 2),
        // v3 : ajout de la table strict_session.
        AutoMigration(from = 2, to = 3),
        // v4 : ajout de la table scroll_guard (anti-doomscrolling).
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class MobileManagerDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
    abstract fun profileDao(): ProfileDao
    abstract fun strictSessionDao(): StrictSessionDao
    abstract fun scrollGuardDao(): ScrollGuardDao

    companion object {
        const val NAME = "mobile_manager.db"
    }
}
