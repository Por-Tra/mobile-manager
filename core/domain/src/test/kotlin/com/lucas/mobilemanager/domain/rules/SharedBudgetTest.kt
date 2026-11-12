package com.lucas.mobilemanager.domain.rules

import com.lucas.mobilemanager.domain.mode.AppMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZonedDateTime

class SharedBudgetTest {
    private val now = ZonedDateTime.parse("2026-11-12T12:00:00+01:00[Europe/Paris]")
    private fun minutes(value: Long) = Duration.ofMinutes(value)
    private val engine = RuleEngine()
    private val budget = SharedBudget(setOf("a", "b"), minutes(45), minutes(40))
    private fun decide(
        request: AppRequest = AppRequest("a"),
        layers: List<PolicyLayer> = emptyList(),
        shared: SharedBudget = budget,
        mode: AppMode = AppMode.RESTRICTED,
        whitelist: Set<String> = emptySet(),
    ) = engine.evaluate(ResolvedPolicy(layers), request, SystemWhitelist(whitelist), now, mode, shared)
    private fun global(action: RuleAction) = PolicyLayer(LayerSource.Global, listOf(Rule(Target.App("a"), action)), null)

    @Test fun `selected apps share the same remaining budget`() {
        assertEquals(Decision.Allowed(minutes(5)), decide())
        assertEquals(Decision.Allowed(minutes(5)), decide(AppRequest("b", usageToday = minutes(2))))
        assertEquals(Decision.Allowed(null), decide(AppRequest("other")))
    }

    @Test fun `budget exhaustion explains the shared limit and resets at local midnight`() {
        val decision = decide(shared = budget.copy(used = minutes(45))) as Decision.Blocked
        assertEquals(BlockReason.DailyLimitReached(minutes(45), sharedBudget = true), decision.reason)
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant(), decision.until)
    }

    @Test fun `zone usage is not added to app budget`() {
        assertEquals(Decision.Allowed(minutes(5)), decide(AppRequest("a", zone = "reels", zoneUsageToday = minutes(200))))
    }

    @Test fun `free mode whitelist and allow exceptions bypass budget`() {
        val exhausted = budget.copy(used = minutes(90))
        assertEquals(Decision.Allowed(null), decide(shared = exhausted, mode = AppMode.FREE))
        assertEquals(Decision.Allowed(null), decide(shared = exhausted, whitelist = setOf("a")))
        assertEquals(Decision.Allowed(null), decide(shared = exhausted, layers = listOf(global(RuleAction.Allow))))
    }

    @Test fun `allow below a restriction preserves that restriction`() {
        val higher = PolicyLayer(LayerSource.ManualProfile(1, "Work"), listOf(Rule(Target.App("a"), RuleAction.Limit(minutes(10)))), null)
        assertEquals(Decision.Allowed(minutes(8)), decide(AppRequest("a", usageToday = minutes(2)), listOf(higher, global(RuleAction.Allow))))
    }

    @Test fun `earliest app or shared limit wins while block stays strongest`() {
        assertEquals(Decision.Allowed(minutes(2)), decide(layers = listOf(global(RuleAction.Limit(minutes(2))))))
        assertEquals(Decision.Allowed(minutes(5)), decide(layers = listOf(global(RuleAction.Limit(minutes(60))))))
        assertEquals(BlockReason.GlobalRule, (decide(layers = listOf(global(RuleAction.Block))) as Decision.Blocked).reason)
        val strict = PolicyLayer(LayerSource.StrictSession, listOf(Rule(Target.App("a"), RuleAction.Block)), now.plusHours(1).toInstant())
        assertEquals(BlockReason.StrictSession, (decide(layers = listOf(strict, global(RuleAction.Allow))) as Decision.Blocked).reason)
    }

    @Test fun `zone allow keeps its specificity`() {
        val layer = PolicyLayer(LayerSource.Global, listOf(Rule(Target.Zone("a", "messages"), RuleAction.Allow)), null)
        assertEquals(Decision.Allowed(null), decide(AppRequest("a", zone = "messages"), listOf(layer), budget.copy(used = minutes(45))))
    }
}
