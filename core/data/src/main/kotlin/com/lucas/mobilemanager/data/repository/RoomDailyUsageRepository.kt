package com.lucas.mobilemanager.data.repository

import com.lucas.mobilemanager.data.db.DailyUsageDao
import com.lucas.mobilemanager.data.db.DailyUsageEntity
import com.lucas.mobilemanager.domain.repository.DailyUsageRepository
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

class RoomDailyUsageRepository @Inject constructor(
    private val dao: DailyUsageDao,
) : DailyUsageRepository {

    override suspend fun storedDays(from: LocalDate, to: LocalDate): Set<LocalDate> =
        dao.storedDays(from.toString(), to.toString()).map(LocalDate::parse).toSet()

    override suspend fun usageBetween(from: LocalDate, to: LocalDate): Map<LocalDate, Map<String, Duration>> =
        dao.between(from.toString(), to.toString())
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, rows) ->
                rows.filter { it.packageName != DailyUsageEntity.NO_USAGE }
                    .associate { it.packageName to Duration.ofSeconds(it.seconds) }
            }

    override suspend fun store(date: LocalDate, usage: Map<String, Duration>) {
        val rows = usage.filterValues { it.seconds > 0 }
            .map { (pkg, duration) -> DailyUsageEntity(date.toString(), pkg, duration.seconds) }
            .ifEmpty { listOf(DailyUsageEntity(date.toString(), DailyUsageEntity.NO_USAGE, 0)) }
        dao.insertAll(rows)
    }
}
