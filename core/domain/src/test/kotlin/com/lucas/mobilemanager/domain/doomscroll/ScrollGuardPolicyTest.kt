package com.lucas.mobilemanager.domain.doomscroll

import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.PARIS
import com.lucas.mobilemanager.domain.TIKTOK
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.rules.BlockReason
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.usage.ContinuousSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class ScrollGuardPolicyTest {

    private fun min(m: Long) = Duration.ofMinutes(m)
    private val start = at(MONDAY, 10).toInstant()
    private fun t(minutes: Long): Instant = start + min(minutes)

    private val tiktok = ScrollGuard(
        packageName = TIKTOK,
        pauseEvery = min(5),
        sessionLimit = min(10),
        cooldown = min(15),
        dailyLimit = min(30),
    )

    private fun evaluate(
        guard: ScrollGuard = tiktok,
        sessionMinutes: Long? = 0,
        usageToday: Duration = Duration.ZERO,
        lastPauseAt: Instant? = null,
        cooldownUntil: Instant? = null,
    ): GuardDecision {
        val now = t(sessionMinutes ?: 0)
        val session = sessionMinutes?.let { ContinuousSession(start, min(it)) }
        return ScrollGuardPolicy.evaluate(guard, session, usageToday, lastPauseAt, cooldownUntil, now, PARIS)
    }

    @Nested
    inner class Pauses {
        @Test
        fun `no pause before the first milestone`() =
            assertEquals(GuardDecision.None(min(1)), evaluate(sessionMinutes = 4))

        @Test
        fun `pause at the first milestone`() =
            assertEquals(GuardDecision.Pause(min(5)), evaluate(sessionMinutes = 5))

        @Test
        fun `no second pause in the same milestone`() =
            assertEquals(GuardDecision.None(min(3)), evaluate(sessionMinutes = 7, lastPauseAt = t(5)))

        @Test
        fun `a pause from a previous session does not count`() =
            assertEquals(GuardDecision.Pause(min(5)), evaluate(sessionMinutes = 5, lastPauseAt = t(-30)))

        @Test
        fun `next milestone triggers a new pause`() {
            val guard = tiktok.copy(sessionLimit = null)
            assertEquals(GuardDecision.Pause(min(10)), evaluate(guard, sessionMinutes = 10, lastPauseAt = t(5)))
        }
    }

    @Nested
    inner class Blocks {
        @Test
        fun `session limit blocks for the cooldown`() = assertEquals(
            GuardDecision.Blocked(BlockReason.SessionLimitReached(min(10)), t(10) + min(15)),
            evaluate(sessionMinutes = 10, lastPauseAt = t(5)),
        )

        @Test
        fun `cooldown in progress blocks even without a session`() = assertEquals(
            GuardDecision.Blocked(BlockReason.SessionLimitReached(min(10)), t(20)),
            evaluate(sessionMinutes = null, cooldownUntil = t(20)),
        )

        @Test
        fun `finished cooldown lets the app open again`() =
            assertEquals(GuardDecision.None(min(5)), evaluate(sessionMinutes = 0, cooldownUntil = t(0)))

        @Test
        fun `daily limit blocks until midnight`() = assertEquals(
            GuardDecision.Blocked(BlockReason.DailyLimitReached(min(30)), at(MONDAY.plusDays(1), 0).toInstant()),
            evaluate(sessionMinutes = 1, usageToday = min(30)),
        )
    }

    @Nested
    inner class NextCheck {
        @Test
        fun `next check is the closest event`() {
            // Palier de pause dans 2 min, limite de session dans 7 min, limite quotidienne dans 1 min.
            assertEquals(GuardDecision.None(min(1)), evaluate(sessionMinutes = 3, usageToday = min(29), lastPauseAt = null))
        }

        @Test
        fun `guard without any setting never acts`() = assertEquals(
            GuardDecision.None(null),
            evaluate(ScrollGuard(TIKTOK), sessionMinutes = 120, usageToday = Duration.ofHours(5)),
        )
    }

    @Nested
    inner class Combine {
        private val pause = GuardDecision.Pause(min(5))

        @Test
        fun `blocked by a profile stays blocked`() {
            val engine = Decision.Blocked(BlockReason.Profile(1, "Études"), null)
            assertEquals(engine, ScrollGuardPolicy.combine(engine, pause))
        }

        @Test
        fun `allowed app is still subject to the guard`() =
            assertEquals(Decision.Pause(min(5)), ScrollGuardPolicy.combine(Decision.Allowed(null), pause))

        @Test
        fun `guard block replaces an allowed decision`() {
            val guard = GuardDecision.Blocked(BlockReason.SessionLimitReached(min(10)), t(25))
            assertEquals(
                Decision.Blocked(BlockReason.SessionLimitReached(min(10)), t(25)),
                ScrollGuardPolicy.combine(Decision.Allowed(min(20)), guard),
            )
        }

        @Test
        fun `next guard milestone is passed along`() = assertEquals(
            Decision.Allowed(min(20), checkAgainIn = min(2)),
            ScrollGuardPolicy.combine(Decision.Allowed(min(20)), GuardDecision.None(min(2))),
        )
    }
}
