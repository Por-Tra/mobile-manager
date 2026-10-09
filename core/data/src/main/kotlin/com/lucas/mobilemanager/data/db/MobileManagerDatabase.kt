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
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2 : ajout des tables profile, profile_schedule et rule.
        AutoMigration(from = 1, to = 2),
        // v3 : ajout de la table strict_session.
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class MobileManagerDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
    abstract fun profileDao(): ProfileDao
    abstract fun strictSessionDao(): StrictSessionDao

    companion object {
        const val NAME = "mobile_manager.db"
    }
}
