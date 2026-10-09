package com.lucas.mobilemanager.zones

import com.lucas.mobilemanager.domain.repository.ZoneUsageRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.domain.zones.ZoneKey
import com.lucas.mobilemanager.domain.zones.ZoneUsageAccumulator
import com.lucas.mobilemanager.domain.zones.ZoneUsageDelta
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temps passé dans les zones, pour les quotas. Les constats s'accumulent en mémoire et sont
 * écrits dans `zone_usage` par paquets (toutes les [FLUSH_EVERY] de temps compté, et à la sortie
 * de l'app) : pas d'écriture disque à chaque vérification.
 */
@Singleton
class ZoneUsageRecorder @Inject constructor(
    private val repository: ZoneUsageRepository,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()
    private val accumulator = ZoneUsageAccumulator()
    private val pending = ArrayList<ZoneUsageDelta>()

    /** Constat : l'app [packageName] affiche [zone] (`null` = hors zone). */
    suspend fun onSnapshot(packageName: String, zone: String?) = mutex.withLock {
        pending += accumulator.onSnapshot(packageName, zone, timeProvider.now(), timeProvider.zone())
        if (pending.fold(Duration.ZERO) { sum, it -> sum + it.duration } >= FLUSH_EVERY) flushLocked()
    }

    /** L'app a quitté le premier plan ou l'écran s'est éteint : on clôt et on enregistre. */
    suspend fun onLeft() = mutex.withLock {
        pending += accumulator.onLeft(timeProvider.now(), timeProvider.zone())
        flushLocked()
    }

    /** Temps passé aujourd'hui dans la zone, y compris ce qui n'est pas encore écrit. */
    suspend fun usageToday(packageName: String, zone: String): Duration = mutex.withLock {
        val today = timeProvider.localNow().toLocalDate()
        val stored = repository.usageOn(today)[ZoneKey(packageName, zone)] ?: Duration.ZERO
        pending.filter { it.date == today && it.packageName == packageName && it.zone == zone }
            .fold(stored) { sum, it -> sum + it.duration }
    }

    private suspend fun flushLocked() {
        if (pending.isEmpty()) return
        repository.add(pending.toList())
        pending.clear()
    }

    private companion object {
        val FLUSH_EVERY: Duration = Duration.ofSeconds(15)
    }
}
