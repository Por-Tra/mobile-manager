package com.lucas.mobilemanager.goals

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.lucas.mobilemanager.dashboard.DailyUsageSnapshotter
import com.lucas.mobilemanager.domain.onboarding.PermissionKind
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus
import com.lucas.mobilemanager.domain.repository.DailyUsageRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.permissions.PermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

data class NetworkVolume(val wifi: Long, val mobile: Long)
data class GoalReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val previousDays: Int,
    val usage: Duration,
    val previousUsage: Duration?,
    val network: NetworkVolume?,
    val previousNetwork: NetworkVolume?,
    val sharedUid: Boolean,
)

class GoalReportLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeProvider,
    private val usage: DailyUsageRepository,
    private val snapshots: DailyUsageSnapshotter,
    private val permissions: PermissionChecker,
) {
    suspend fun load(packages: Set<String>): GoalReport = withContext(Dispatchers.IO) {
        val today = time.localNow().toLocalDate()
        val from = today.minusDays(7)
        val previousFrom = today.minusDays(14)
        val granted = permissions.status(PermissionKind.USAGE_STATS) == PermissionStatus.GRANTED
        if (granted) snapshots.ensureSnapshots()
        val days = if (granted) usage.usageBetween(previousFrom, today.minusDays(1)) else emptyMap()
        val current = days.filterKeys { !it.isBefore(from) }
        val previous = days.filterKeys { it.isBefore(from) }
        fun total(values: Map<LocalDate, Map<String, Duration>>) = values.values.fold(Duration.ZERO) { sum, apps ->
            sum + packages.fold(Duration.ZERO) { s, pkg -> s + (apps[pkg] ?: Duration.ZERO) }
        }
        val uids = packages.mapNotNull { pkg -> try {
            context.packageManager.getApplicationInfo(pkg, 0).uid
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null } }.toSet()
        val shared = uids.any { context.packageManager.getPackagesForUid(it).orEmpty().size > 1 }
        // Une app désinstallée rend la sélection incomplète : aucun zéro inventé.
        val resolved = packages.all { pkg -> runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess }
        val network = if (granted && resolved) network(uids, from, today) else null
        val previousNetwork = if (granted && resolved) network(uids, previousFrom, from) else null
        GoalReport(from, today.minusDays(1), current.size, previous.size, total(current),
            total(previous).takeIf { current.size == 7 && previous.size == 7 }, network, previousNetwork, shared)
    }

    @Suppress("DEPRECATION")
    private fun network(uids: Set<Int>, from: LocalDate, to: LocalDate): NetworkVolume? = try {
        val manager = context.getSystemService(NetworkStatsManager::class.java)
        fun bytes(type: Int): Long {
            var total = 0L
            manager.querySummary(type, null, from.atStartOfDay(time.zone()).toInstant().toEpochMilli(),
                to.atStartOfDay(time.zone()).toInstant().toEpochMilli()).use { stats ->
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    if (bucket.uid in uids) total += bucket.rxBytes.coerceAtLeast(0) + bucket.txBytes.coerceAtLeast(0)
                }
            }
            return total
        }
        NetworkVolume(bytes(ConnectivityManager.TYPE_WIFI), bytes(ConnectivityManager.TYPE_MOBILE))
    } catch (_: SecurityException) { null
    } catch (_: RuntimeException) { null }
}
