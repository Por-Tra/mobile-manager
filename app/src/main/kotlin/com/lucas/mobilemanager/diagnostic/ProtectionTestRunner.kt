package com.lucas.mobilemanager.diagnostic

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import com.lucas.mobilemanager.MainActivity
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.zones.ZoneMatcher
import com.lucas.mobilemanager.enforcement.EnforcementCoordinator
import com.lucas.mobilemanager.zones.AccessibilitySnapshot
import com.lucas.mobilemanager.zones.ZoneDetectorStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sonde bornée, indépendante du suivi d'activité : elle peut révéler un suivi muet. */
class ProtectionTestRunner(
    private val service: AccessibilityService,
    private val store: ProtectionTestStore,
    private val detectors: ZoneDetectorStore,
    private val coordinator: EnforcementCoordinator,
) {
    fun start(scope: CoroutineScope) = scope.launch {
        store.run.map { it?.takeIf { run -> run.active }?.id }.distinctUntilChanged().collectLatest { id ->
            if (id == null) return@collectLatest
            val run = store.run.value ?: return@collectLatest
            store.update(id) { it.copy(configurationReady = configure(service)) }
            val overlay = DiagnosticOverlay(service, onCapture = {
                store.finish(id)
                service.startActivity(Intent(service, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_PROTECTION_TEST, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }, actionLabel = R.string.probe_results)
            try {
                while (store.run.value?.let { it.id == id && it.active } == true) {
                    val usable = service.getSystemService(PowerManager::class.java).isInteractive &&
                        !service.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    val root = if (usable) service.rootInActiveWindow else null
                    if (root?.packageName?.toString() == run.packageName) {
                        if (overlay.show()) store.update(id) { it.copy(overlayAttached = true) }
                        overlay.update(service.applicationContext.getString(R.string.probe_overlay))
                        val snapshot = withContext(Dispatchers.Default) { AccessibilitySnapshot.capture(root, 800) }
                        // Vérifier à nouveau la fenêtre après le parcours, qui peut être lent.
                        if (service.rootInActiveWindow?.packageName?.toString() == run.packageName) {
                            val match = ZoneMatcher.detect(detectors.detectors.value, run.packageName, snapshot.nodes)
                            store.update(id) { it.snapshot(snapshot.nodes.size, snapshot.nodes.count { node -> node.viewId != null },
                                match?.zone?.id == run.zoneId) }
                            if (match?.zone?.id == run.zoneId) {
                                val decision = withContext(Dispatchers.Default) { coordinator.evaluateZone(run.packageName, run.zoneId) }
                                store.detection(run.packageName, run.zoneId, blocked = decision is Decision.Blocked)
                                overlay.update(service.applicationContext.getString(R.string.probe_zone_seen))
                            }
                        }
                    } else {
                        overlay.hide()
                    }
                    delay(1_000)
                }
            } catch (error: RuntimeException) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                // Une app peut retirer sa fenêtre pendant la lecture. Conserver les preuves
                // partielles et permettre un nouvel essai, sans arrêter le service.
                store.finish(id)
            } finally {
                overlay.hide()
            }
        }
    }

    companion object {
        /** Réapplique uniquement les options déjà déclarées ; n'accorde aucune autorisation. */
        fun configure(service: AccessibilityService): Boolean = try {
            val info = service.serviceInfo
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.notificationTimeout = 250
            service.serviceInfo = info
            val actual = service.serviceInfo
            actual.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0 &&
                actual.flags and AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS != 0 &&
                actual.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS != 0 &&
                actual.eventTypes and AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED != 0 &&
                actual.eventTypes and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED != 0
        } catch (_: RuntimeException) {
            false
        }
    }
}
