package com.lucas.mobilemanager.domain.session

import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.TIKTOK
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy.OverrideState
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy.ValidationError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class StrictSessionPolicyTest {

    private val start = at(MONDAY, 9).toInstant()
    private fun minutes(m: Long) = Duration.ofMinutes(m)

    @Nested
    inner class Validation {
        private fun validate(end: java.time.Instant, apps: Set<String> = setOf(TIKTOK), delay: Duration = minutes(5)) =
            StrictSessionPolicy.validateNew(start, end, apps, delay)

        @Test
        fun `valid session has no errors`() = assertEquals(emptySet<ValidationError>(), validate(start + minutes(25)))

        @Test
        fun `exactly twelve hours is allowed`() =
            assertEquals(emptySet<ValidationError>(), validate(start + Duration.ofHours(12)))

        @Test
        fun `more than twelve hours is refused`() =
            assertEquals(setOf(ValidationError.TOO_LONG), validate(start + Duration.ofHours(12) + minutes(1)))

        @Test
        fun `end in the past is refused`() =
            assertTrue(ValidationError.END_NOT_IN_FUTURE in validate(start - minutes(1)))

        @Test
        fun `no apps is refused`() =
            assertEquals(setOf(ValidationError.NO_APPS), validate(start + minutes(25), apps = emptySet()))

        @Test
        fun `reflection delay must stay between two and ten minutes`() {
            assertEquals(setOf(ValidationError.DELAY_OUT_OF_RANGE), validate(start + minutes(25), delay = minutes(1)))
            assertEquals(setOf(ValidationError.DELAY_OUT_OF_RANGE), validate(start + minutes(25), delay = minutes(11)))
            assertEquals(emptySet<ValidationError>(), validate(start + minutes(25), delay = minutes(2)))
            assertEquals(emptySet<ValidationError>(), validate(start + minutes(25), delay = minutes(10)))
        }
    }

    @Nested
    inner class Override {
        private fun session(requestedAt: java.time.Instant?) = StrictSession(
            startedAt = start,
            endsAt = start + Duration.ofHours(2),
            blockedPackages = setOf(TIKTOK),
            reflectionDelay = minutes(5),
            overrideRequestedAt = requestedAt,
        )

        private val requested = start + minutes(30)

        @Test
        fun `nothing requested`() =
            assertEquals(OverrideState.NotRequested, StrictSessionPolicy.overrideState(session(null), requested))

        @Test
        fun `waiting during the reflection delay`() = assertEquals(
            OverrideState.Waiting(requested + minutes(5)),
            StrictSessionPolicy.overrideState(session(requested), requested + minutes(4)),
        )

        @Test
        fun `available right after the delay for two minutes`() {
            val expected = OverrideState.Available(requested + minutes(7))
            assertEquals(expected, StrictSessionPolicy.overrideState(session(requested), requested + minutes(5)))
            assertEquals(expected, StrictSessionPolicy.overrideState(session(requested), requested + minutes(6)))
        }

        @Test
        fun `expires after the two minute window`() = assertEquals(
            OverrideState.Expired,
            StrictSessionPolicy.overrideState(session(requested), requested + minutes(7)),
        )
    }

    @Nested
    inner class Running {
        private val session = StrictSession(
            startedAt = start,
            endsAt = start + Duration.ofHours(1),
            blockedPackages = setOf(TIKTOK),
            reflectionDelay = minutes(5),
        )

        @Test
        fun `running between start and end`() {
            assertTrue(session.isRunning(start))
            assertTrue(session.isRunning(start + minutes(59)))
            assertFalse(session.isRunning(start + minutes(60)))
        }

        @Test
        fun `closed session is not running even before its end time`() =
            assertFalse(
                session.copy(endedAt = start + minutes(10), endReason = StrictSession.EndReason.OVERRIDDEN)
                    .isRunning(start + minutes(20)),
            )
    }
}
