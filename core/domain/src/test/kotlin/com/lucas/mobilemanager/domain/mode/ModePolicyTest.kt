package com.lucas.mobilemanager.domain.mode

import com.lucas.mobilemanager.domain.INSTAGRAM
import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.block
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.profile
import com.lucas.mobilemanager.domain.rules.*
import com.lucas.mobilemanager.domain.rules.Target
import java.time.Duration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ModePolicyTest {
    private val now = at(MONDAY, 10)
    private val semi = ModeSettings(AppMode.SEMI_FREE, minimumWaitMinutes = 60, unlockRequestedAt = now.toInstant())

    @Test
    fun `no mode is imposed before an explicit choice`() {
        assertNull(ModeSettings().selectedMode)
        assertEquals(AppMode.FREE, ModeSettings().effectiveMode)
        assertFalse(ModePolicy.blocksEnabled(ModeSettings()))
    }

    @Test
    fun `waiting never bypasses an existing pin or an active session`() {
        val after = now.plusHours(2).toInstant()
        assertFalse(ModePolicy.canUnlockAfterWait(semi, hasPin = true, sessionRunning = false, after))
        assertFalse(ModePolicy.canUnlockAfterWait(semi, hasPin = false, sessionRunning = true, after))
    }

    @Test
    fun `wait starts at request and is inclusive at its end`() {
        assertFalse(ModePolicy.canUnlockAfterWait(semi.copy(unlockRequestedAt = null), false, false, now.plusDays(1).toInstant()))
        assertFalse(ModePolicy.canUnlockAfterWait(semi, false, false, now.plusMinutes(59).toInstant()))
        assertTrue(ModePolicy.canUnlockAfterWait(semi, false, false, now.plusHours(1).toInstant()))
        assertFalse(ModePolicy.canUnlockAfterWait(semi, false, false, now.minusSeconds(1).toInstant()))
    }

    @Test
    fun `only semi free supports waiting and all configured delays are validated`() {
        for (mode in AppMode.entries) {
            assertEquals(mode == AppMode.SEMI_FREE, ModePolicy.requiresWait(ModeSettings(mode), false))
            assertFalse(ModePolicy.requiresWait(ModeSettings(mode), true))
        }
        assertThrows(IllegalArgumentException::class.java) { ModeSettings(minimumWaitMinutes = 0) }
        assertThrows(IllegalArgumentException::class.java) { ModeSettings(minimumWaitMinutes = 1441) }
    }

    @Test
    fun `semi free modifications require an unlock even after the request wait is over`() {
        val after = now.plusHours(2).toInstant()
        assertFalse(ModePolicy.canModify(semi, false, null, after))
        assertTrue(ModePolicy.canModify(semi, false, after.plusSeconds(1), after))
        assertFalse(ModePolicy.canModify(semi, false, after, after))
        for (mode in listOf(AppMode.FREE, AppMode.RESTRICTED)) {
            assertTrue(ModePolicy.canModify(ModeSettings(mode), false, null, after))
            assertFalse(ModePolicy.canModify(ModeSettings(mode), true, null, after))
            assertTrue(ModePolicy.canModify(ModeSettings(mode), true, after.plusSeconds(1), after))
        }
    }

    @Test
    fun `free disables app profile strict session and exhausted zone quota together`() {
        val session = StrictSession(startedAt = now.minusHours(1).toInstant(), endsAt = now.plusHours(1).toInstant(), blockedPackages = setOf(INSTAGRAM), reflectionDelay = Duration.ofMinutes(5))
        val policy = ProfileResolver().resolve(
            profiles = listOf(profile(1, rules = listOf(block(INSTAGRAM)))),
            globalRules = listOf(Rule(Target.Zone(INSTAGRAM, "reels"), RuleAction.Limit(Duration.ofMinutes(1)))),
            manualActivation = null, strictSession = session, now = now,
        )
        val request = AppRequest(INSTAGRAM, "reels", Duration.ofHours(2), Duration.ofHours(1))
        val engine = RuleEngine()
        val whitelist = SystemWhitelist(emptySet())
        assertEquals(Decision.Allowed(null), engine.evaluate(policy, request, whitelist, now, AppMode.FREE))
        for (mode in listOf(AppMode.SEMI_FREE, AppMode.RESTRICTED)) {
            assertTrue(engine.evaluate(policy, request, whitelist, now, mode) is Decision.Blocked)
        }
        assertEquals(Decision.Allowed(null), engine.evaluate(policy, request, SystemWhitelist(setOf(INSTAGRAM)), now, AppMode.RESTRICTED))
    }

    @Test
    fun `unlock request itself never disables a blocking rule`() {
        val policy = ProfileResolver().resolve(emptyList(), listOf(block(INSTAGRAM)), null, null, now)
        assertTrue(ModePolicy.canUnlockAfterWait(semi, false, false, now.plusHours(1).toInstant()))
        assertTrue(RuleEngine().evaluate(policy, AppRequest(INSTAGRAM), SystemWhitelist(emptySet()), now, semi.effectiveMode) is Decision.Blocked)
    }
}
