package com.lucas.mobilemanager.dashboard

import com.lucas.mobilemanager.domain.dashboard.DashboardCalculator
import com.lucas.mobilemanager.domain.dashboard.DashboardInput
import com.lucas.mobilemanager.domain.dashboard.DashboardSummary
import com.lucas.mobilemanager.domain.dashboard.Period
import com.lucas.mobilemanager.domain.repository.DailyUsageRepository
import com.lucas.mobilemanager.domain.repository.EventLogRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.enforcement.SystemWhitelistProvider
import com.lucas.mobilemanager.enforcement.UsageStatsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Rassemble les données du tableau de bord (instantanés, jour en cours en direct, journal). */
@Singleton
class DashboardLoader @Inject constructor(
    private val snapshotter: DailyUsageSnapshotter,
    private val dailyUsageRepository: DailyUsageRepository,
    private val usageStatsReader: UsageStatsReader,
    private val eventLogRepository: EventLogRepository,
    private val strictSessionRepository: StrictSessionRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val whitelistProvider: SystemWhitelistProvider,
    private val timeProvider: TimeProvider,
) {
    suspend fun load(period: Period): DashboardSummary = withContext(Dispatchers.IO) {
        snapshotter.ensureSnapshots()
        val now = timeProvider.localNow()
        val today = now.toLocalDate()
        val zone = timeProvider.zone()
        // Deux semaines : la période affichée et la précédente, pour la comparaison.
        val from = today.minusDays(13)
        val stored = dailyUsageRepository.usageBetween(from, today.minusDays(1))
        val todayUsage = usageStatsReader.usageByPackageOn(today)
        val usage = if (todayUsage != null) stored + (today to todayUsage) else stored

        val start = from.atStartOfDay(zone).toInstant()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant()
        DashboardCalculator.summarize(
            DashboardInput(
                period = period,
                today = today,
                zone = zone,
                dailyUsage = usage,
                events = eventLogRepository.observeBetween(start, end).first(),
                sessions = strictSessionRepository.sessionsStartedBetween(start, end),
                dailyGoal = preferencesRepository.preferences.first().screenTimeGoalMinutes?.let { Duration.ofMinutes(it.toLong()) },
                excludedPackages = whitelistProvider.nonScreenTimePackages(),
            ),
        )
    }
}
