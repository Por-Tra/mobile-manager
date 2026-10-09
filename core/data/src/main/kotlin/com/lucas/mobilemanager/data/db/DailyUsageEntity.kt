package com.lucas.mobilemanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Temps d'écran d'une app un jour donné (jour local `aaaa-mm-jj`). Une ligne [NO_USAGE] marque
 * un jour enregistré sans aucun usage, pour le distinguer d'un jour sans données.
 */
@Entity(tableName = "daily_usage", primaryKeys = ["date", "package_name"])
data class DailyUsageEntity(
    val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    val seconds: Long,
) {
    companion object {
        const val NO_USAGE = "__none__"
    }
}

@Dao
interface DailyUsageDao {
    @Query("SELECT DISTINCT date FROM daily_usage WHERE date BETWEEN :from AND :to")
    suspend fun storedDays(from: String, to: String): List<String>

    @Query("SELECT * FROM daily_usage WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<DailyUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<DailyUsageEntity>)
}
