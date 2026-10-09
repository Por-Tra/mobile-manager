package com.lucas.mobilemanager.domain.rules

import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.PARIS
import com.lucas.mobilemanager.domain.at
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class EnforcementPolicyTest {

    @Nested
    inner class NextCheck {
        @Test
        fun `blocked app is not rechecked`() =
            assertNull(EnforcementPolicy.nextCheckDelay(Decision.Blocked(BlockReason.GlobalRule, null)))

        @Test
        fun `unlimited app is rechecked every 30 seconds`() =
            assertEquals(Duration.ofSeconds(30), EnforcementPolicy.nextCheckDelay(Decision.Allowed(null)))

        @Test
        fun `app close to its limit is rechecked when the limit is reached`() =
            assertEquals(
                Duration.ofSeconds(12),
                EnforcementPolicy.nextCheckDelay(Decision.Allowed(Duration.ofSeconds(12))),
            )

        @Test
        fun `long remaining time is capped at 30 seconds`() =
            assertEquals(
                Duration.ofSeconds(30),
                EnforcementPolicy.nextCheckDelay(Decision.Allowed(Duration.ofMinutes(20))),
            )

        @Test
        fun `pause screen is not rechecked`() =
            assertNull(EnforcementPolicy.nextCheckDelay(Decision.Pause(Duration.ofMinutes(5))))

        @Test
        fun `next scroll guard milestone shortens the delay`() =
            assertEquals(
                Duration.ofSeconds(8),
                EnforcementPolicy.nextCheckDelay(Decision.Allowed(Duration.ofMinutes(20), checkAgainIn = Duration.ofSeconds(8))),
            )

        @Test
        fun `tiny remaining time waits at least one second`() =
            assertEquals(
                Duration.ofSeconds(1),
                EnforcementPolicy.nextCheckDelay(Decision.Allowed(Duration.ofMillis(10))),
            )
    }

    @Nested
    inner class EndRelation {
        private val now = at(MONDAY, 22).toInstant()

        @Test
        fun `later today`() =
            assertEquals(DayRelation.TODAY, BlockEnd.relation(at(MONDAY, 23).toInstant(), now, PARIS))

        @Test
        fun `next midnight is tomorrow`() =
            assertEquals(DayRelation.TOMORROW, BlockEnd.relation(at(MONDAY.plusDays(1), 0).toInstant(), now, PARIS))

        @Test
        fun `in two days is later`() =
            assertEquals(DayRelation.LATER, BlockEnd.relation(at(MONDAY.plusDays(2), 9).toInstant(), now, PARIS))
    }
}
