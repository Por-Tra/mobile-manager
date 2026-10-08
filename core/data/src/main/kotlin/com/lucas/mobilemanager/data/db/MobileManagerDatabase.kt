package com.lucas.mobilemanager.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EventLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MobileManagerDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao

    companion object {
        const val NAME = "mobile_manager.db"
    }
}
