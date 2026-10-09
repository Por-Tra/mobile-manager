package com.lucas.mobilemanager.domain.lock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class LockTest {

    @Nested
    inner class Hasher {
        private val config = PinHasher.newConfig("2468")

        @Test
        fun `right pin matches, wrong pin does not`() {
            assertTrue(PinHasher.matches("2468", config))
            assertFalse(PinHasher.matches("2469", config))
            assertFalse(PinHasher.matches("", config))
        }

        @Test
        fun `pin is never stored in clear and each config has its own salt`() {
            val other = PinHasher.newConfig("2468")
            assertFalse(config.pinHash.contains("2468"))
            assertNotEquals(config.salt, other.salt)
            assertNotEquals(config.pinHash, other.pinHash)
            assertTrue(PinHasher.matches("2468", other))
        }

        @Test
        fun `iteration count is part of the stored config`() {
            assertEquals(PinHasher.ITERATIONS, config.iterations)
            assertFalse(PinHasher.matches("2468", config.copy(iterations = PinHasher.LEGACY_ITERATIONS)))
        }

        @Test
        fun `corrupted config never matches`() =
            assertFalse(PinHasher.matches("2468", config.copy(pinHash = "pas du base64 !")))
    }

    @Nested
    inner class Policy {
        private val now = Instant.parse("2026-10-12T10:00:00Z")
        private val config = LockConfig(pinHash = "h", salt = "s")
        private val right: (String, LockConfig) -> Boolean = { pin, _ -> pin == "1234" }

        @Test
        fun `pin is 4 to 8 digits`() {
            assertTrue(LockPolicy.isValidPin("1234"))
            assertTrue(LockPolicy.isValidPin("12345678"))
            assertFalse(LockPolicy.isValidPin("123"))
            assertFalse(LockPolicy.isValidPin("123456789"))
            assertFalse(LockPolicy.isValidPin("12a4"))
        }

        @Test
        fun `right pin succeeds and resets failures`() {
            val (result, updated) = LockPolicy.attempt(config.copy(failures = 3), "1234", now, right)
            assertEquals(LockPolicy.Attempt.Success, result)
            assertEquals(0, updated.failures)
        }

        @Test
        fun `four wrong pins are free, the fifth imposes 30 seconds`() {
            var current = config
            repeat(4) { i ->
                val (result, updated) = LockPolicy.attempt(current, "0000", now, right)
                assertEquals(LockPolicy.Attempt.Wrong(attemptsLeft = 4 - i), result)
                current = updated
            }
            val (result, updated) = LockPolicy.attempt(current, "0000", now, right)
            assertEquals(LockPolicy.Attempt.LockedOut(now.plusSeconds(30)), result)
            assertEquals(5, updated.failures)
        }

        @Test
        fun `during the wait even the right pin is refused`() {
            val locked = config.copy(failures = 5, lockedOutUntil = now.plusSeconds(30))
            assertEquals(LockPolicy.Attempt.LockedOut(now.plusSeconds(30)), LockPolicy.attempt(locked, "1234", now, right).first)
            assertEquals(LockPolicy.Attempt.Success, LockPolicy.attempt(locked, "1234", now.plusSeconds(30), right).first)
        }

        @Test
        fun `wait doubles after each failure, up to one hour`() {
            assertNull(LockPolicy.lockoutFor(4))
            assertEquals(Duration.ofSeconds(30), LockPolicy.lockoutFor(5))
            assertEquals(Duration.ofSeconds(60), LockPolicy.lockoutFor(6))
            assertEquals(Duration.ofMinutes(2), LockPolicy.lockoutFor(7))
            assertEquals(Duration.ofHours(1), LockPolicy.lockoutFor(20))
            assertEquals(Duration.ofHours(1), LockPolicy.lockoutFor(1_000))
        }
    }
}
