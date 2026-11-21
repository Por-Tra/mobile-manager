package com.lucas.mobilemanager.domain.goals

import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class PreparationSlot(val days: Set<DayOfWeek>, val start: LocalTime, val minutes: Int) {
    init {
        require(days.isNotEmpty())
        require(minutes in 1..1440)
    }
}

data class PersonalGoal(
    val id: String,
    val title: String,
    val dueDate: LocalDate,
    val packages: Set<String>,
    val dailyBudgetMinutes: Int = 30,
    val slots: List<PreparationSlot> = emptyList(),
    val remindersEnabled: Boolean = false,
    val completed: Boolean = false,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && title.length <= 120)
        require(packages.isNotEmpty() && packages.all { it.isNotBlank() })
        require(dailyBudgetMinutes in 1..1440)
    }

    // La date d'échéance est incluse ; aucune modification des autres règles à la fin.
    fun isActive(date: LocalDate): Boolean = !completed && !date.isAfter(dueDate)

    fun nextPreparation(now: ZonedDateTime): ZonedDateTime? = (0L..7L).flatMap { offset ->
        val day = now.toLocalDate().plusDays(offset)
        if (!isActive(day)) emptyList() else slots.filter { day.dayOfWeek in it.days }
            .map { day.atTime(it.start).atZone(now.zone) }
    }.filter { !it.isBefore(now) }.minOrNull()
}

data class GoalBudget(val goal: PersonalGoal, val used: Duration)

/** Dérogation conservatrice : un redémarrage ou un recul de l'horloge la rend caduque. */
data class TemporaryAccess(
    val packageName: String,
    val startedAt: Instant,
    val until: Instant,
    val bootCount: Int,
    val elapsedUntilMs: Long,
) {
    fun remaining(now: Instant, currentBoot: Int, elapsedMs: Long): Duration? {
        if (currentBoot != bootCount || now.isBefore(startedAt) || !now.isBefore(until) || elapsedMs >= elapsedUntilMs) return null
        return minOf(Duration.between(now, until), Duration.ofMillis(elapsedUntilMs - elapsedMs))
    }
}

object TemporaryAccessPolicy {
    fun canRequest(minutes: Int, unlocked: Boolean, strictSessionRunning: Boolean): Boolean =
        minutes in 1..1440 && unlocked && !strictSessionRunning
}

interface GoalRepository {
    val goals: Flow<List<PersonalGoal>>
    val temporaryAccess: Flow<List<TemporaryAccess>>
    suspend fun save(goal: PersonalGoal)
    suspend fun delete(id: String)
    suspend fun grant(access: TemporaryAccess)
    suspend fun revoke(packageName: String)
    /** Réserve un rappel de façon atomique, afin de ne pas le répéter. */
    suspend fun claimReminder(key: String, date: LocalDate): Boolean
}

/** Énergie de décharge du téléphone entier ; aucune attribution à une application. */
object ResponsibleMetrics {
    fun dischargedWh(beforeNanoWh: Long, afterNanoWh: Long): Double? =
        if (beforeNanoWh <= 0 || afterNanoWh <= 0 || afterNanoWh > beforeNanoWh) null
        else (beforeNanoWh - afterNanoWh) / 1_000_000_000.0

    fun rechargeGrams(energyWh: Double, efficiency: Double, intensityGramsPerKwh: Double): Double {
        require(energyWh.isFinite() && energyWh >= 0)
        require(efficiency.isFinite() && efficiency > 0 && efficiency <= 1)
        require(intensityGramsPerKwh.isFinite() && intensityGramsPerKwh >= 0)
        return energyWh / efficiency / 1000 * intensityGramsPerKwh
    }
}
