package com.lucas.mobilemanager.dashboard

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucas.mobilemanager.domain.repository.DailyUsageRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.enforcement.UsageStatsReader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fige le temps d'écran des jours passés dans `daily_usage`. Android ne garde les événements
 * détaillés qu'environ une semaine : sans ces instantanés, impossible de comparer deux semaines.
 */
@Singleton
class DailyUsageSnapshotter @Inject constructor(
    private val usageStatsReader: UsageStatsReader,
    private val repository: DailyUsageRepository,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()

    /** Enregistre chaque jour des 14 derniers (hier inclus) qui manque encore, si Android a les données. */
    suspend fun ensureSnapshots() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val today = timeProvider.localNow().toLocalDate()
            val from = today.minusDays(DAYS_KEPT)
            val yesterday = today.minusDays(1)
            val stored = repository.storedDays(from, yesterday)
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(yesterday) }
                .filter { it !in stored }
                .forEach { day -> usageStatsReader.usageByPackageOn(day)?.let { repository.store(day, it) } }
        }
    }

    companion object {
        const val DAYS_KEPT = 14L
    }
}

/** Instantané quotidien, même si l'app n'est pas ouverte pendant plusieurs jours. */
@HiltWorker
class DailyUsageWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val snapshotter: DailyUsageSnapshotter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        snapshotter.ensureSnapshots()
        return Result.success()
    }

    companion object {
        private const val NAME = "daily_usage_snapshot"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyUsageWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
