package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.MONDAY
import com.lucas.mobilemanager.domain.SPRING_FORWARD
import com.lucas.mobilemanager.domain.at
import com.lucas.mobilemanager.domain.time
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ManualActivationPolicyTest {

    @Test
    fun `one and two hours from now`() {
        val now = at(MONDAY, 14)
        assertEquals(at(MONDAY, 15).toInstant(), ManualActivationPolicy.until(ManualDuration.ONE_HOUR, now))
        assertEquals(at(MONDAY, 16).toInstant(), ManualActivationPolicy.until(ManualDuration.TWO_HOURS, now))
    }

    @Test
    fun `until a later time today`() = assertEquals(
        at(MONDAY, 18).toInstant(),
        ManualActivationPolicy.until(ManualDuration.Until(time(18)), at(MONDAY, 14)),
    )

    @Test
    fun `until a time already passed means tomorrow`() = assertEquals(
        at(MONDAY.plusDays(1), 9).toInstant(),
        ManualActivationPolicy.until(ManualDuration.Until(time(9)), at(MONDAY, 14)),
    )

    @Test
    fun `until the current minute means tomorrow`() = assertEquals(
        at(MONDAY.plusDays(1), 14).toInstant(),
        ManualActivationPolicy.until(ManualDuration.Until(time(14)), at(MONDAY, 14)),
    )

    @Test
    fun `until turned off has no end`() =
        assertNull(ManualActivationPolicy.until(ManualDuration.UntilTurnedOff, at(MONDAY, 14)))

    @Test
    fun `one hour across the spring forward gap is one real hour`() = assertEquals(
        at(SPRING_FORWARD, 1, 30).toInstant().plusSeconds(3600),
        ManualActivationPolicy.until(ManualDuration.ONE_HOUR, at(SPRING_FORWARD, 1, 30)),
    )

    @Test
    fun `nonexistent local time is shifted forward`() = assertEquals(
        at(SPRING_FORWARD, 3, 30).toInstant(),
        ManualActivationPolicy.until(ManualDuration.Until(time(2, 30)), at(SPRING_FORWARD, 1)),
    )
}
