package com.lucas.mobilemanager.data.repository

import com.lucas.mobilemanager.data.db.ZoneUsageDao
import com.lucas.mobilemanager.data.db.ZoneUsageEntity
import com.lucas.mobilemanager.domain.repository.ZoneUsageRepository
import com.lucas.mobilemanager.domain.zones.ZoneKey
import com.lucas.mobilemanager.domain.zones.ZoneUsageDelta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

class RoomZoneUsageRepository @Inject constructor(
    private val dao: ZoneUsageDao,
) : ZoneUsageRepository {

    override suspend fun add(deltas: List<ZoneUsageDelta>) {
        // Secondes entières, au plus proche : les fractions s'équilibrent sur la journée.
        deltas.groupBy { Triple(it.date, it.packageName, it.zone) }.forEach { (key, parts) ->
            val millis = parts.sumOf { it.duration.toMillis() }
            val seconds = (millis + 500) / 1000
            if (seconds > 0) dao.add(key.first.toString(), key.second, key.third, seconds)
        }
    }

    override fun observeDay(date: LocalDate): Flow<Map<ZoneKey, Duration>> =
        dao.observeDay(date.toString()).map { rows -> rows.toUsage() }

    override suspend fun usageOn(date: LocalDate): Map<ZoneKey, Duration> =
        dao.between(date.toString(), date.toString()).toUsage()

    override suspend fun usageBetween(from: LocalDate, to: LocalDate): Map<LocalDate, Map<ZoneKey, Duration>> =
        dao.between(from.toString(), to.toString())
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, rows) -> rows.toUsage() }

    private fun List<ZoneUsageEntity>.toUsage(): Map<ZoneKey, Duration> =
        associate { ZoneKey(it.packageName, it.zone) to Duration.ofSeconds(it.seconds) }
}
