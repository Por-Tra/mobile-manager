package com.lucas.mobilemanager.domain.zones

/** Preuves techniques d'un test explicite, sans contenu de l'écran ni changement de règle. */
data class ProtectionProbe(
    val events: Int = 0,
    val snapshots: Int = 0,
    val maxVisibleNodes: Int = 0,
    val maxViewIds: Int = 0,
    val zoneSeen: Boolean = false,
    val restrictionSeen: Boolean = false,
    val actionAccepted: Boolean = false,
    val configurationReady: Boolean = false,
    val overlayAttached: Boolean = false,
    val overlayConfirmed: Boolean = false,
    val protectionConfirmed: Boolean = false,
    val fallbackUsed: Boolean = false,
    val entryMasked: Boolean = false,
) {
    enum class Finding {
        SERVICE_DISCONNECTED, CONFIGURATION, NO_EVENTS, NO_WINDOW, NO_VISIBLE_NODES,
        ENTRY_MASKED, ZONE_NOT_SEEN, ALLOWED, ACTION_NOT_SEEN, CONFIRM_OVERLAY, CONFIRM_ACTION, OBSERVED,
    }

    fun finding(connected: Boolean): Finding = when {
        !connected -> Finding.SERVICE_DISCONNECTED
        !configurationReady -> Finding.CONFIGURATION
        events == 0 -> Finding.NO_EVENTS
        snapshots == 0 -> Finding.NO_WINDOW
        maxVisibleNodes == 0 -> Finding.NO_VISIBLE_NODES
        !zoneSeen -> if (entryMasked) Finding.ENTRY_MASKED else Finding.ZONE_NOT_SEEN
        !restrictionSeen -> Finding.ALLOWED
        !actionAccepted -> Finding.ACTION_NOT_SEEN
        !overlayAttached || !overlayConfirmed -> Finding.CONFIRM_OVERLAY
        !protectionConfirmed -> Finding.CONFIRM_ACTION
        else -> Finding.OBSERVED
    }

    fun snapshot(nodes: Int, ids: Int, matched: Boolean) = copy(
        snapshots = snapshots + 1,
        maxVisibleNodes = maxOf(maxVisibleNodes, nodes),
        maxViewIds = maxOf(maxViewIds, ids),
        zoneSeen = zoneSeen || matched,
    )
}
