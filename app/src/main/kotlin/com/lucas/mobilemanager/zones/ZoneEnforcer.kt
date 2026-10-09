package com.lucas.mobilemanager.zones

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.diagnostic.DiagnosticOverlay
import com.lucas.mobilemanager.diagnostic.HierarchyCapture
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.domain.zones.ExitStep
import com.lucas.mobilemanager.domain.zones.UiNode
import com.lucas.mobilemanager.domain.zones.ZoneExitPlanner
import com.lucas.mobilemanager.domain.zones.ZoneExitThrottle
import com.lucas.mobilemanager.domain.zones.ZoneMatch
import com.lucas.mobilemanager.domain.zones.ZoneMatcher
import com.lucas.mobilemanager.domain.zones.ZoneVisitTracker
import com.lucas.mobilemanager.enforcement.EnforcementCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sort automatiquement des zones bloquées (Reels, Shorts…) d'une app autorisée.
 * Lit la structure de l'écran (identifiants, libellés d'onglets, état sélectionné) des seules
 * apps qui ont des détecteurs ; rien n'est enregistré.
 */
class ZoneEnforcer(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val coordinator: EnforcementCoordinator,
    private val detectorStore: ZoneDetectorStore,
    private val timeProvider: TimeProvider,
    /** Repli si la sortie échoue plusieurs fois : écran de blocage classique. */
    private val onEscalate: (packageName: String, decision: Decision.Blocked) -> Unit,
) {
    /** Bandeau du mode diagnostic, s'il est affiché. */
    var overlay: DiagnosticOverlay? = null

    private var checkJob: Job? = null
    private val throttles = HashMap<String, ZoneExitThrottle>()
    private val visits = ZoneVisitTracker()
    private var lastToastAtMs = 0L
    private val labels = HashMap<String, String>()

    /** Changement de fenêtre ou de contenu. Les rafales sont regroupées (≤ 1 vérification / 300 ms). */
    fun onEvent(packageName: String) {
        val watched = packageName in detectorStore.detectors.value.packages
        if (!watched && overlay == null) return
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            delay(CHECK_DELAY_MS)
            check()
        }
    }

    /** Une autre app (ou Mobile Manager) est passée au premier plan : la visite en cours se termine. */
    fun onForegroundChanged() {
        visits.onAppLeft(timeProvider.now())?.let { scope.launch(Dispatchers.Default) { coordinator.recordZoneVisit(it) } }
    }

    private suspend fun check() {
        val root = service.rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        val detectors = detectorStore.detectors.value
        val watched = pkg in detectors.packages
        if (!watched && overlay == null) return

        val snapshot = AccessibilitySnapshot.capture(root, MAX_NODES)
        val match = if (watched) ZoneMatcher.detect(detectors, pkg, snapshot.nodes) else null
        overlay?.update(statusText(pkg, match))
        if (!watched) return

        val now = timeProvider.now()
        val throttle = throttles.getOrPut(pkg) { ZoneExitThrottle() }
        if (match == null) {
            throttle.onZoneLeft()
            visits.onSnapshot(pkg, null, now)?.let { coordinator.recordZoneVisit(it) }
            return
        }

        val decision = withContext(Dispatchers.Default) { coordinator.evaluateZone(pkg, match.zone.id) }
        if (decision !is Decision.Blocked) {
            // Zone autorisée (par un profil, ou réglée « Autorisée ») : on mesure le temps passé.
            visits.onSnapshot(pkg, match.zone.id, now)?.let { coordinator.recordZoneVisit(it) }
            return
        }
        visits.onSnapshot(pkg, null, now)?.let { coordinator.recordZoneVisit(it) }

        when (throttle.onZoneDetected(now)) {
            ZoneExitThrottle.Action.WAIT -> Unit
            ZoneExitThrottle.Action.EXIT -> {
                exit(ZoneExitPlanner.plan(match.zone, snapshot.nodes, throttle.attempts - 1), snapshot.infos)
                showToast(match.zone.label)
                withContext(Dispatchers.Default) { coordinator.recordZoneRedirect(pkg, match.zone.id) }
            }
            ZoneExitThrottle.Action.ESCALATE -> {
                throttle.onZoneLeft()
                onEscalate(pkg, decision)
            }
        }
    }

    /** Capture complète de la fenêtre au premier plan, pour le mode diagnostic. */
    fun capture(): HierarchyCapture? {
        val root = service.rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        val snapshot = AccessibilitySnapshot.capture(root, MAX_NODES_DIAGNOSTIC, includeInvisible = true)
        val match = ZoneMatcher.detect(detectorStore.detectors.value, pkg, snapshot.nodes)
        return HierarchyCapture(pkg, snapshot.nodes, match, timeProvider.now())
    }

    private fun exit(step: ExitStep, infos: List<AccessibilityNodeInfo>) {
        if (step is ExitStep.Click) {
            var node: AccessibilityNodeInfo? = infos.getOrNull(step.nodeIndex)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        }
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun showToast(zoneLabel: String) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastToastAtMs < TOAST_INTERVAL_MS) return
        lastToastAtMs = nowMs
        Toast.makeText(service, service.getString(R.string.zone_hidden, zoneLabel), Toast.LENGTH_SHORT).show()
    }

    private fun statusText(pkg: String, match: ZoneMatch?): String {
        val label = labels.getOrPut(pkg) {
            try {
                service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
        }
        return if (match == null) {
            service.getString(R.string.diagnostic_overlay_no_zone, label)
        } else {
            service.getString(R.string.diagnostic_overlay_zone, label, match.zone.label, match.signalIndex + 1)
        }
    }

    private companion object {
        const val CHECK_DELAY_MS = 300L
        const val TOAST_INTERVAL_MS = 3_000L
        const val MAX_NODES = 800
        const val MAX_NODES_DIAGNOSTIC = 2_000
    }
}

/**
 * Instantané à plat de l'arbre d'accessibilité (ordre de lecture, avec profondeur).
 * Les nœuds hors écran (onglets qu'Instagram garde en mémoire…) et leurs enfants sont ignorés,
 * sauf pour le mode diagnostic qui les affiche marqués comme invisibles.
 */
class AccessibilitySnapshot private constructor(
    val nodes: List<UiNode>,
    /** Nœuds Android correspondants, pour pouvoir cliquer. */
    val infos: List<AccessibilityNodeInfo>,
) {
    companion object {
        fun capture(root: AccessibilityNodeInfo, maxNodes: Int, includeInvisible: Boolean = false): AccessibilitySnapshot {
            val nodes = ArrayList<UiNode>()
            val infos = ArrayList<AccessibilityNodeInfo>()
            val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>().apply { addLast(root to 0) }
            while (stack.isNotEmpty() && nodes.size < maxNodes) {
                val (node, depth) = stack.removeLast()
                val visible = node.isVisibleToUser
                if (!visible && !includeInvisible) continue
                nodes += UiNode(
                    viewId = node.viewIdResourceName,
                    text = node.text?.toString(),
                    desc = node.contentDescription?.toString(),
                    selected = node.isSelected,
                    clickable = node.isClickable,
                    depth = depth,
                    className = node.className?.toString(),
                    visible = visible,
                )
                infos += node
                for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
            return AccessibilitySnapshot(nodes, infos)
        }
    }
}
