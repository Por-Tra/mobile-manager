package com.lucas.mobilemanager.domain.rules

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

class HabitSupportTest {
    @Test fun `pause does not restart for another activity of the same app`() {
        val state = OpeningPauseState()
        assertTrue(state.onForeground("a"))
        assertTrue(state.needsPause("a"))
        state.continueOpening("a")
        assertFalse(state.onForeground("a"))
        assertFalse(state.needsPause("a"))
        state.onForeground("b")
        state.onForeground("a")
        assertTrue(state.needsPause("a"))
    }

    @Test fun `screen off reset revokes previous continuation`() {
        val state = OpeningPauseState()
        state.onForeground("a")
        state.continueOpening("a")
        state.reset()
        state.onForeground("a")
        assertTrue(state.needsPause("a"))
        state.continueOpening("b")
        assertTrue(state.needsPause("a"))
    }

    @Test fun `warning is once per app or zone per local day`() {
        val policy = LimitWarningPolicy()
        val date = LocalDate.of(2026, 10, 25)
        assertTrue(policy.shouldWarn(date, "app", Duration.ofMinutes(5), true))
        assertFalse(policy.shouldWarn(date, "app", Duration.ofMinutes(1), true))
        assertTrue(policy.shouldWarn(date, "zone", Duration.ofMinutes(2), true))
        assertTrue(policy.shouldWarn(date.plusDays(1), "app", Duration.ofMinutes(5), true))
    }

    @Test fun `disabled unbounded exhausted or distant limits do not consume warning`() {
        val policy = LimitWarningPolicy()
        val date = LocalDate.of(2026, 11, 12)
        for (remaining in listOf(null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMinutes(6))) {
            assertFalse(policy.shouldWarn(date, "app", remaining, true))
        }
        assertFalse(policy.shouldWarn(date, "app", Duration.ofMinutes(3), false))
        assertTrue(policy.shouldWarn(date, "app", Duration.ofMinutes(3), true))
    }
}
