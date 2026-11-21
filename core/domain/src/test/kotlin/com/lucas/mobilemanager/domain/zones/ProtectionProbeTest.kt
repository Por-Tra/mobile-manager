package com.lucas.mobilemanager.domain.zones

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtectionProbeTest {
    private val observed = ProtectionProbe(events = 2, snapshots = 3, maxVisibleNodes = 12,
        zoneSeen = true, restrictionSeen = true, actionAccepted = true,
        configurationReady = true, overlayAttached = true, overlayConfirmed = true, protectionConfirmed = true)

    @Test fun `a connected service alone never proves protection`() {
        assertEquals(ProtectionProbe.Finding.CONFIGURATION, ProtectionProbe().finding(true))
        assertEquals(ProtectionProbe.Finding.NO_EVENTS, ProtectionProbe(configurationReady = true).finding(true))
    }

    @Test fun `each missing link has its own diagnosis`() {
        val cases = listOf(
            observed.copy(configurationReady = false) to ProtectionProbe.Finding.CONFIGURATION,
            observed.copy(events = 0) to ProtectionProbe.Finding.NO_EVENTS,
            observed.copy(snapshots = 0) to ProtectionProbe.Finding.NO_WINDOW,
            observed.copy(maxVisibleNodes = 0) to ProtectionProbe.Finding.NO_VISIBLE_NODES,
            observed.copy(zoneSeen = false) to ProtectionProbe.Finding.ZONE_NOT_SEEN,
            observed.copy(zoneSeen = false, entryMasked = true) to ProtectionProbe.Finding.ENTRY_MASKED,
            observed.copy(restrictionSeen = false, actionAccepted = true) to ProtectionProbe.Finding.ALLOWED,
            observed.copy(actionAccepted = false) to ProtectionProbe.Finding.ACTION_NOT_SEEN,
            observed.copy(overlayAttached = false) to ProtectionProbe.Finding.CONFIRM_OVERLAY,
            observed.copy(overlayConfirmed = false) to ProtectionProbe.Finding.CONFIRM_OVERLAY,
            observed.copy(protectionConfirmed = false) to ProtectionProbe.Finding.CONFIRM_ACTION,
        )
        cases.forEach { (probe, finding) -> assertEquals(finding, probe.finding(true)) }
    }

    @Test fun `disconnection invalidates previous successful evidence`() {
        assertEquals(ProtectionProbe.Finding.SERVICE_DISCONNECTED, observed.finding(false))
        assertEquals(ProtectionProbe.Finding.OBSERVED, observed.finding(true))
    }

    @Test fun `later empty snapshots preserve transient detection`() {
        val probe = ProtectionProbe().snapshot(20, 5, true).snapshot(0, 0, false)
        assertEquals(2, probe.snapshots)
        assertEquals(20, probe.maxVisibleNodes)
        assertEquals(5, probe.maxViewIds)
        assertTrue(probe.zoneSeen)
    }
}
