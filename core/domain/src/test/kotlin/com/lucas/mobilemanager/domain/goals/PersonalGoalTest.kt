package com.lucas.mobilemanager.domain.goals

import com.lucas.mobilemanager.domain.mode.AppMode
import com.lucas.mobilemanager.domain.rules.*
import com.lucas.mobilemanager.domain.rules.Target
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.*

class PersonalGoalTest {
    private val now = ZonedDateTime.parse("2026-12-10T19:00:00+01:00[Europe/Paris]")
    private val goal = PersonalGoal("code", "Passer le code", LocalDate.of(2026, 12, 11), setOf("a", "b"))
    private fun minutes(n: Long) = Duration.ofMinutes(n)
    private fun decision(
        goals: List<GoalBudget> = listOf(GoalBudget(goal, minutes(30))),
        layers: List<PolicyLayer> = emptyList(),
        request: AppRequest = AppRequest("a"),
        mode: AppMode = AppMode.RESTRICTED,
        whitelist: Set<String> = emptySet(),
        at: ZonedDateTime = now,
        shared: SharedBudget? = null,
    ) = RuleEngine().evaluate(ResolvedPolicy(layers), request, SystemWhitelist(whitelist), at, mode, shared, goals)

    @Test fun `completion and local date expiry remove only the goal budget`() {
        assertTrue(goal.isActive(goal.dueDate))
        assertFalse(goal.isActive(goal.dueDate.plusDays(1)))
        assertEquals(Decision.Allowed(null), decision(at = now.plusDays(2)))
        assertEquals(Decision.Allowed(null), decision(goals = listOf(GoalBudget(goal.copy(completed = true), minutes(40)))))
        val block = PolicyLayer(LayerSource.Global, listOf(Rule(Target.App("a"), RuleAction.Block)), null)
        assertEquals(BlockReason.GlobalRule, (decision(layers = listOf(block), at = now.plusDays(2)) as Decision.Blocked).reason)
    }

    @Test fun `goal budget uses combined app usage and does not add zone usage`() {
        val budgets = listOf(GoalBudget(goal, minutes(25)))
        assertEquals(Decision.Allowed(minutes(5)), decision(budgets))
        assertEquals(Decision.Allowed(minutes(5)), decision(budgets, request = AppRequest("b", zone = "shorts", zoneUsageToday = minutes(100))))
        assertEquals(Decision.Allowed(null), decision(budgets, request = AppRequest("other")))
        val result = decision() as Decision.Blocked
        assertEquals(goal.title, (result.reason as BlockReason.DailyLimitReached).goalTitle)
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant(), result.until)
    }

    @Test fun `overlapping goals and existing budgets use earliest limit without adding them`() {
        val second = goal.copy(id = "other", dailyBudgetMinutes = 10)
        assertEquals(Decision.Allowed(minutes(2)), decision(listOf(GoalBudget(goal, minutes(20)), GoalBudget(second, minutes(8)))))
        assertEquals(Decision.Allowed(minutes(1)), decision(listOf(GoalBudget(goal, minutes(20))),
            shared = SharedBudget(setOf("a"), minutes(20), minutes(19))))
    }

    @Test fun `free whitelist and specific allow retain precedence`() {
        assertEquals(Decision.Allowed(null), decision(mode = AppMode.FREE))
        assertEquals(Decision.Allowed(null), decision(whitelist = setOf("a")))
        val allow = PolicyLayer(LayerSource.Global, listOf(Rule(Target.Zone("a", "messages"), RuleAction.Allow)), null)
        assertEquals(Decision.Allowed(null), decision(layers = listOf(allow), request = AppRequest("a", zone = "messages")))
        val higher = PolicyLayer(LayerSource.ManualProfile(1, "Travail"), listOf(Rule(Target.App("a"), RuleAction.Limit(minutes(1)))), null)
        assertEquals(Decision.Allowed(minutes(1)), decision(layers = listOf(higher, allow), request = AppRequest("a", zone = "messages")))
        val strict = PolicyLayer(LayerSource.StrictSession, listOf(Rule(Target.App("a"), RuleAction.Block)), now.plusHours(1).toInstant())
        assertEquals(BlockReason.StrictSession, (decision(layers = listOf(strict, allow), request = AppRequest("a", zone = "messages")) as Decision.Blocked).reason)
    }

    @Test fun `preparation finds next day in local zone and stops at expiry or completion`() {
        val g = goal.copy(slots = listOf(PreparationSlot(setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalTime.of(20, 0), 20)))
        assertEquals(now.withHour(20), g.nextPreparation(now))
        assertEquals(now.plusDays(1).withHour(20), g.nextPreparation(now.withHour(21)))
        assertNull(g.nextPreparation(now.plusDays(1).withHour(21)))
        assertNull(g.copy(completed = true).nextPreparation(now))
    }

    @Test fun `preparation follows daylight saving gaps with zone rules`() {
        val before = ZonedDateTime.parse("2027-03-28T01:00:00+01:00[Europe/Paris]")
        val g = goal.copy(dueDate = LocalDate.of(2027, 3, 28), slots = listOf(PreparationSlot(setOf(DayOfWeek.SUNDAY), LocalTime.of(2, 30), 20)))
        assertEquals(3, g.nextPreparation(before)!!.hour)
    }

    @Test fun `temporary access expires at either clock deadline and cannot survive reboot or clock rollback`() {
        val start = now.toInstant()
        val a = TemporaryAccess("a", start, start + minutes(5), 12, 310_000)
        assertEquals(minutes(5), a.remaining(start, 12, 10_000))
        assertEquals(Duration.ofSeconds(1), a.remaining(start + minutes(1), 12, 309_000))
        assertNull(a.remaining(start + minutes(5), 12, 20_000))
        assertNull(a.remaining(start, 12, 310_000))
        assertNull(a.remaining(start, 13, 0))
        assertNull(a.remaining(start.minusSeconds(1), 12, 20_000))
    }

    @Test fun `temporary requests require unlocked settings valid duration and no strict session`() {
        assertTrue(TemporaryAccessPolicy.canRequest(1, true, false))
        assertTrue(TemporaryAccessPolicy.canRequest(1440, true, false))
        assertFalse(TemporaryAccessPolicy.canRequest(0, true, false))
        assertFalse(TemporaryAccessPolicy.canRequest(1441, true, false))
        assertFalse(TemporaryAccessPolicy.canRequest(5, false, false))
        assertFalse(TemporaryAccessPolicy.canRequest(5, true, true))
    }

    @Test fun `energy counter conversion and recharge scenario use Wh and kWh correctly`() {
        assertEquals(2.0, ResponsibleMetrics.dischargedWh(10_000_000_000, 8_000_000_000))
        assertNull(ResponsibleMetrics.dischargedWh(Long.MIN_VALUE, 100))
        assertNull(ResponsibleMetrics.dischargedWh(100, 200))
        assertEquals(0.1, ResponsibleMetrics.rechargeGrams(2.0, 0.8, 40.0), 0.000001)
        assertThrows(IllegalArgumentException::class.java) { ResponsibleMetrics.rechargeGrams(2.0, 0.0, 40.0) }
        assertThrows(IllegalArgumentException::class.java) { ResponsibleMetrics.rechargeGrams(2.0, 0.8, Double.NaN) }
    }
}
