package com.lucas.mobilemanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Temps passé dans une zone (Reels, Shorts…) un jour local donné (`aaaa-mm-jj`). */
@Entity(tableName = "zone_usage", primaryKeys = ["date", "package_name", "zone"])
data class ZoneUsageEntity(
    val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    val zone: String,
    val seconds: Long,
)

@Dao
interface ZoneUsageDao {
    @Query("INSERT OR IGNORE INTO zone_usage (date, package_name, zone, seconds) VALUES (:date, :packageName, :zone, 0)")
    suspend fun ensureRow(date: String, packageName: String, zone: String)

    @Query("UPDATE zone_usage SET seconds = seconds + :seconds WHERE date = :date AND package_name = :packageName AND zone = :zone")
    suspend fun addSeconds(date: String, packageName: String, zone: String, seconds: Long)

    @Transaction
    suspend fun add(date: String, packageName: String, zone: String, seconds: Long) {
        ensureRow(date, packageName, zone)
        addSeconds(date, packageName, zone, seconds)
    }

    @Query("SELECT * FROM zone_usage WHERE date = :date")
    fun observeDay(date: String): Flow<List<ZoneUsageEntity>>

    @Query("SELECT * FROM zone_usage WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<ZoneUsageEntity>
}
