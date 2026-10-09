package com.lucas.mobilemanager.domain.dashboard

import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.EventType
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.zones.ZoneKey
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Period { TODAY, WEEK }

/** Jours locaux [start, endInclusive]. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    val days: List<LocalDate>
        get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(endInclusive) }.toList()

    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)

    /** Instants [début du premier jour, début du lendemain du dernier) ; justes les jours de 23 h ou 25 h. */
    fun instants(zone: ZoneId): Pair<Instant, Instant> =
        start.atStartOfDay(zone).toInstant() to endInclusive.plusDays(1).atStartOfDay(zone).toInstant()
}

object PeriodRange {
    /** Aujourd'hui, ou les 7 derniers jours se terminant aujourd'hui inclus. */
    fun of(period: Period, today: LocalDate): DateRange = when (period) {
        Period.TODAY -> DateRange(today, today)
        Period.WEEK -> DateRange(today.minusDays(6), today)
    }

    /** Période de même longueur, juste avant. */
    fun previous(range: DateRange): DateRange {
        val length = range.days.size.toLong()
        return DateRange(range.start.minusDays(length), range.start.minusDays(1))
    }
}

/** Temps d'écran par jour local, puis par package. Jour absent = pas de données (≠ zéro usage). */
typealias DailyUsage = Map<LocalDate, Map<String, Duration>>

data class DashboardInput(
    val period: Period,
    val today: LocalDate,
    val zone: ZoneId,
    val dailyUsage: DailyUsage,
    /** Événements du journal ; filtrés sur la période par le calculateur. */
    val events: List<EventLogEntry>,
    /** Temps passé dans les zones, par jour local. */
    val zoneUsage: Map<LocalDate, Map<ZoneKey, Duration>>,
    /** Quotas quotidiens en vigueur par zone (règles par défaut). */
    val zoneQuotas: Map<ZoneKey, Duration>,
    val sessions: List<StrictSession>,
    /** Objectif de temps d'écran quotidien, `null` si aucun. */
    val dailyGoal: Duration?,
    /** Ne comptent pas comme temps d'écran (Mobile Manager, launcher, interface système). */
    val excludedPackages: Set<String>,
)

/** Évolution par rapport à la période précédente. Le domaine qualifie, l'UI formule (sans jugement). */
enum class Trend { LESS, MORE, SAME, NO_HISTORY }

data class Comparison(val trend: Trend, val delta: Duration, val previousTotal: Duration)

data class AppUsage(val packageName: String, val duration: Duration, val share: Float)

data class DayTotal(val date: LocalDate, val total: Duration, val hasData: Boolean)

data class ZoneStats(
    /** Temps passé dans les zones sur la période. */
    val timeByZone: Map<ZoneKey, Duration>,
    /** Sorties automatiques des zones bloquées (ou dont le quota est atteint). */
    val exitsByZone: Map<ZoneKey, Int>,
    /** Quota quotidien de chaque zone qui en a un. */
    val quotaByZone: Map<ZoneKey, Duration>,
)

/** Pourquoi l'outil a interrompu un usage. */
enum class BlockKind { RULE, STRICT_SESSION, DAILY_LIMIT }

data class Interruptions(
    val blocks: Map<BlockKind, Int>,
    val zoneExits: Int,
) {
    val total: Int get() = blocks.values.sum() + zoneExits
}

data class Goals(
    /** Jours (avec données) passés sous l'objectif quotidien ; `null` sans objectif. */
    val daysUnderGoal: Int?,
    val daysWithData: Int,
    /** Jours où au moins une limite quotidienne a été atteinte. */
    val daysLimitReached: Int,
    val sessionsCompleted: Int,
    val sessionsEndedEarly: Int,
)

data class DashboardSummary(
    val range: DateRange,
    val total: Duration,
    val comparison: Comparison,
    val apps: List<AppUsage>,
    /** Toujours les 7 derniers jours (graphique), quelle que soit la période. */
    val week: List<DayTotal>,
    val zones: ZoneStats,
    val interruptions: Interruptions,
    val goals: Goals,
) {
    val hasData: Boolean get() = range.days.any { day -> week.any { it.date == day && it.hasData } } || !total.isZero
}

/**
 * Calcule le tableau de bord. Mesure sans juger : les tendances sont des catégories neutres,
 * la formulation est laissée à l'interface.
 */
object DashboardCalculator {

    /** En dessous de cet écart, deux périodes sont considérées comme équivalentes. */
    val SAME_THRESHOLD: Duration = Duration.ofMinutes(5)

    fun summarize(input: DashboardInput): DashboardSummary {
        val range = PeriodRange.of(input.period, input.today)
        val usage = input.dailyUsage.mapValues { (_, apps) -> apps.filterKeys { it !in input.excludedPackages } }

        val perApp = range.days.flatMap { usage[it].orEmpty().entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, durations) -> durations.fold(Duration.ZERO, Duration::plus) }
            .filterValues { !it.isZero }
        val total = perApp.values.fold(Duration.ZERO, Duration::plus)

        val previous = PeriodRange.previous(range)
        val previousHasData = previous.days.any { it in usage }
        val previousTotal = previous.days.sumOf(usage)

        val (from, to) = range.instants(input.zone)
        val events = input.events.filter { !it.occurredAt.isBefore(from) && it.occurredAt.isBefore(to) }

        return DashboardSummary(
            range = range,
            total = total,
            comparison = compare(total, previousTotal, previousHasData),
            apps = perApp.entries
                .sortedByDescending { it.value }
                .map { (pkg, duration) -> AppUsage(pkg, duration, share(duration, total)) },
            week = PeriodRange.of(Period.WEEK, input.today).days.map { day ->
                DayTotal(day, listOf(day).sumOf(usage), day in usage)
            },
            zones = zoneStats(input, range, events),
            interruptions = interruptions(events),
            goals = goals(input, range, usage, events),
        )
    }

    private fun compare(total: Duration, previousTotal: Duration, previousHasData: Boolean): Comparison {
        if (!previousHasData) return Comparison(Trend.NO_HISTORY, Duration.ZERO, previousTotal)
        val delta = total - previousTotal
        val trend = when {
            delta.abs() < SAME_THRESHOLD -> Trend.SAME
            delta.isNegative -> Trend.LESS
            else -> Trend.MORE
        }
        return Comparison(trend, delta.abs(), previousTotal)
    }

    private fun zoneStats(input: DashboardInput, range: DateRange, events: List<EventLogEntry>): ZoneStats {
        val time = range.days.flatMap { input.zoneUsage[it].orEmpty().entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, d) -> d.fold(Duration.ZERO, Duration::plus) }
            .filterValues { !it.isZero }
        val exits = events.filter { it.type == EventType.ZONE_REDIRECTED }
            .mapNotNull { it.zoneKey() }
            .groupingBy { it }
            .eachCount()
        return ZoneStats(time, exits, input.zoneQuotas)
    }

    private fun interruptions(events: List<EventLogEntry>): Interruptions = Interruptions(
        blocks = events.filter { it.type == EventType.APP_BLOCKED }
            .groupingBy { blockKind(it.details) }
            .eachCount(),
        zoneExits = events.count { it.type == EventType.ZONE_REDIRECTED },
    )

    private fun goals(input: DashboardInput, range: DateRange, usage: DailyUsage, events: List<EventLogEntry>): Goals {
        val daysWithData = range.days.filter { it in usage }
        val (from, to) = range.instants(input.zone)
        val sessions = input.sessions.filter { !it.startedAt.isBefore(from) && it.startedAt.isBefore(to) }
        return Goals(
            daysUnderGoal = input.dailyGoal?.let { goal -> daysWithData.count { listOf(it).sumOf(usage) <= goal } },
            daysWithData = daysWithData.size,
            daysLimitReached = events
                .filter { it.type == EventType.APP_BLOCKED && blockKind(it.details) == BlockKind.DAILY_LIMIT }
                .map { it.occurredAt.atZone(input.zone).toLocalDate() }
                .distinct()
                .size,
            sessionsCompleted = sessions.count { it.endReason == StrictSession.EndReason.COMPLETED },
            sessionsEndedEarly = sessions.count { it.endReason == StrictSession.EndReason.OVERRIDDEN },
        )
    }

    /** Détails écrits par `EnforcementCoordinator.recordBlock` (« profile:3 », « daily_limit:30 »…). */
    internal fun blockKind(details: String?): BlockKind = when {
        details == null -> BlockKind.RULE
        details.startsWith("strict_session") -> BlockKind.STRICT_SESSION
        details.startsWith("daily_limit") -> BlockKind.DAILY_LIMIT
        else -> BlockKind.RULE
    }

    private fun EventLogEntry.zoneKey(): ZoneKey? {
        val pkg = packageName ?: return null
        val zone = zone ?: return null
        return ZoneKey(pkg, zone)
    }

    private fun List<LocalDate>.sumOf(usage: DailyUsage): Duration =
        flatMap { usage[it].orEmpty().values }.fold(Duration.ZERO, Duration::plus)

    private fun share(part: Duration, total: Duration): Float =
        if (total.isZero) 0f else part.toMillis().toFloat() / total.toMillis()
}
