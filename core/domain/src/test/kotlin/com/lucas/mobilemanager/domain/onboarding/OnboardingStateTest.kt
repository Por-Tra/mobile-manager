package com.lucas.mobilemanager.domain.onboarding

import com.lucas.mobilemanager.domain.onboarding.PermissionKind.ACCESSIBILITY
import com.lucas.mobilemanager.domain.onboarding.PermissionKind.BATTERY_OPTIMIZATION
import com.lucas.mobilemanager.domain.onboarding.PermissionKind.DEVICE_ADMIN
import com.lucas.mobilemanager.domain.onboarding.PermissionKind.NOTIFICATIONS
import com.lucas.mobilemanager.domain.onboarding.PermissionKind.OVERLAY
import com.lucas.mobilemanager.domain.onboarding.PermissionKind.USAGE_STATS
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus.GRANTED
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus.MISSING
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus.NOT_APPLICABLE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingStateTest {

    private fun allWith(status: PermissionStatus) =
        PermissionKind.entries.associateWith { status }

    @Test
    fun `nothing granted - cannot monitor and accessibility comes first`() {
        val state = OnboardingState.from(allWith(MISSING))

        assertFalse(state.canMonitor)
        assertFalse(state.strictModeAvailable)
        assertEquals(ACCESSIBILITY, state.nextMissing)
        assertEquals(0f, state.progress)
    }

    @Test
    fun `missing entries are treated as missing`() {
        val state = OnboardingState.from(emptyMap())

        assertEquals(PermissionKind.entries.size, state.items.size)
        assertTrue(state.items.all { it.status == MISSING })
    }

    @Test
    fun `required permissions are enough to monitor`() {
        val state = OnboardingState.from(
            mapOf(
                ACCESSIBILITY to GRANTED,
                USAGE_STATS to GRANTED,
                BATTERY_OPTIMIZATION to GRANTED,
            ),
        )

        assertTrue(state.canMonitor)
        assertFalse(state.strictModeAvailable)
        assertEquals(OVERLAY, state.nextMissing)
    }

    @Test
    fun `strict mode needs device admin on top of required permissions`() {
        val statuses = allWith(GRANTED).toMutableMap()
        statuses[OVERLAY] = MISSING

        val state = OnboardingState.from(statuses)

        assertTrue(state.strictModeAvailable)
        assertEquals(OVERLAY, state.nextMissing)
    }

    @Test
    fun `device admin alone does not enable strict mode`() {
        val state = OnboardingState.from(mapOf(DEVICE_ADMIN to GRANTED))

        assertFalse(state.canMonitor)
        assertFalse(state.strictModeAvailable)
    }

    @Test
    fun `required permissions are suggested before recommended ones`() {
        val state = OnboardingState.from(
            mapOf(
                ACCESSIBILITY to GRANTED,
                USAGE_STATS to GRANTED,
                BATTERY_OPTIMIZATION to MISSING,
                OVERLAY to MISSING,
            ),
        )

        assertEquals(BATTERY_OPTIMIZATION, state.nextMissing)
    }

    @Test
    fun `not applicable permissions are excluded from progress and count as satisfied`() {
        val statuses = allWith(GRANTED).toMutableMap()
        statuses[NOTIFICATIONS] = NOT_APPLICABLE

        val state = OnboardingState.from(statuses)

        assertEquals(PermissionKind.entries.size - 1, state.applicableCount)
        assertEquals(1f, state.progress)
        assertNull(state.nextMissing)
        assertTrue(state.strictModeAvailable)
    }

    @Test
    fun `progress counts granted over applicable`() {
        val state = OnboardingState.from(
            mapOf(
                ACCESSIBILITY to GRANTED,
                USAGE_STATS to GRANTED,
                NOTIFICATIONS to NOT_APPLICABLE,
            ),
        )

        assertEquals(2f / 5f, state.progress)
    }
}
