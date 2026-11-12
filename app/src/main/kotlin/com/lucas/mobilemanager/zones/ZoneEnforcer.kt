package com.lucas.mobilemanager.zones

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import com.lucas.mobilemanager.BuildConfig
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.diagnostic.DiagnosticOverlay
import com.lucas.mobilemanager.diagnostic.HierarchyCapture
import com.lucas.mobilemanager.domain.rules.BlockReason
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.domain.zones.ExitStep
import com.lucas.mobilemanager.domain.zones.InstagramFeedPolicy
import com.lucas.mobilemanager.domain.zones.UiNode
import com.lucas.mobilemanager.domain.zones.ZoneDefinition
import com.lucas.mobilemanager.domain.zones.ZoneExitPlanner
import com.lucas.mobilemanager.domain.zones.ZoneExitThrottle
import com.lucas.mobilemanager.domain.zones.ZoneMatch
import com.lucas.mobilemanager.domain.zones.ZoneMatcher
import com.lucas.mobilemanager.enforcement.EnforcementCoordinator
import com.lucas.mobilemanager.ui.format.Formats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * Sort automatiquement des zones bloquées ou dont le quota est atteint (Reels, Shorts…) d'une
 * app autorisée, masque leurs points d'entrée et mesure le temps passé dans les zones permises.
 * Lit la structure de l'écran (identifiants, libellés d'onglets, état sélectionné) des seules
 * apps qui ont des détecteurs ; rien n'est enregistré.
 */
class ZoneEnforcer(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val coordinator: EnforcementCoordinator,
    private val detectorStore: ZoneDetectorStore,
    private val timeProvider: TimeProvider,
    private val usageRecorder: ZoneUsageRecorder,
    /** Repli si la sortie échoue plusieurs fois : écran de blocage classique. */
    private val onEscalate: (packageName: String, decision: Decision.Blocked) -> Unit,
    private val limitWarnings: com.lucas.mobilemanager.enforcement.LimitWarningNotifier,
) {
    /** Bandeau du mode diagnostic, s'il est affiché. */
    var overlay: DiagnosticOverlay? = null

    private var checkJob: Job? = null
    private var foregroundPackage: String? = null
    /** Vérification périodique tant qu'une zone permise est à l'écran (une vidéo ne génère pas d'événement). */
    private var tickJob: Job? = null
    private val throttles = HashMap<String, ZoneExitThrottle>()
    private var lastToastAtMs = 0L
    private val labels = HashMap<String, String>()
    private val mask = EntryPointMask(service)
    private var maskShown = false
    private val feedShield = InstagramFeedShield(service, ::openFollowingFeed)
    private var followingJob: Job? = null

    /** Dernière décision par zone (`pkg/zone`) et son heure, pour ne pas réévaluer à chaque événement. */
    private val zoneDecisions = HashMap<String, Pair<Decision, Long>>()

    /** Contenu identique aux vérifications précédentes : on espace les suivantes (défilement du fil). */
    private var lastSignature: Any? = null
    private var unchangedChecks = 0

    /**
     * Changement de fenêtre ou de contenu. Les rafales sont regroupées : une vérification toutes
     * les 400 ms, espacées jusqu'à 1,2 s tant que l'écran ne change pas (zone, boutons).
     */
    fun onEvent(packageName: String, windowChanged: Boolean) {
        if (foregroundPackage == null) return
        val watched = packageName in detectorStore.detectors.value.packages
        // Un masque affiché doit disparaître si une autre fenêtre (volet…) passe devant.
        if (!watched && overlay == null && !maskShown && !feedShield.isShown) return
        if (windowChanged) unchangedChecks = 0
        if (checkJob?.isActive == true) return
        val interval = when {
            unchangedChecks >= SLOWEST_AFTER -> SLOWEST_CHECK_DELAY_MS
            unchangedChecks >= SLOWER_AFTER -> SLOWER_CHECK_DELAY_MS
            else -> CHECK_DELAY_MS
        }
        checkJob = scope.launch {
            delay(interval)
            check()
        }
    }

    /** Une autre app (ou Mobile Manager) est passée au premier plan, ou l'écran s'est éteint. */
    fun onForegroundChanged() {
        foregroundPackage = null
        checkJob?.cancel()
        hideMask()
        tickJob?.cancel()
        followingJob?.cancel()
        zoneDecisions.clear()
        lastSignature = null
        unchangedChecks = 0
        throttles.clear()
        scope.launch { usageRecorder.onLeft() }
    }

    /** Reprend la détection et les masques avec des décisions fraîches, même dans la même app. */
    fun recheck(packageName: String) {
        if (foregroundPackage != packageName) {
            onForegroundChanged()
        } else {
            checkJob?.cancel()
            tickJob?.cancel()
            zoneDecisions.clear()
            lastSignature = null
            unchangedChecks = 0
        }
        foregroundPackage = packageName
        onEvent(packageName, windowChanged = true)
    }

    private suspend fun check() {
        val root = service.rootInActiveWindow ?: return hideMask()
        val pkg = root.packageName?.toString() ?: return hideMask()
        if (pkg != foregroundPackage) return hideMask()
        val detectors = detectorStore.detectors.value
        val watched = pkg in detectors.packages
        if (!watched) hideMask()
        if (!watched && overlay == null) return

        // Lire l'arbre = un aller-retour vers l'app par nœud : hors du thread principal, et
        // seulement les identifiants utiles quand les détecteurs le permettent (Instagram).
        val exactIds = if (watched && overlay == null) ZoneMatcher.exactViewIds(detectors, pkg)?.let {
            if (pkg == InstagramFeedPolicy.PACKAGE) it + InstagramFeedPolicy.viewIds else it
        } else null
        val snapshot = withContext(Dispatchers.Default) {
            if (exactIds != null) AccessibilitySnapshot.captureByIds(root, exactIds) else AccessibilitySnapshot.capture(root, MAX_NODES)
        }
        if (foregroundPackage != pkg) return
        val match = if (watched) ZoneMatcher.detect(detectors, pkg, snapshot.nodes) else null
        if (BuildConfig.DEBUG && match != null) {
            val ids = match.nodeIndices.map { snapshot.nodes[it].viewId?.substringAfter(":id/") ?: snapshot.nodes[it].desc }
            Log.d(TAG, "$pkg zone ${match.zone.id} signal ${match.signalIndex + 1} $ids")
        }
        val signature = Triple(pkg, match?.zone?.id, snapshot.nodes)
        unchangedChecks = if (signature == lastSignature) unchangedChecks + 1 else 0
        lastSignature = signature
        overlay?.update(statusText(pkg, match))
        if (!watched) return

        val now = timeProvider.now()
        val throttle = throttles.getOrPut(pkg) { ZoneExitThrottle() }
        // Le temps passé dans la zone depuis le constat précédent est compté avant de décider :
        // le quota est jugé sur l'usage à jour.
        usageRecorder.onSnapshot(pkg, match?.zone?.id)
        val decision = match?.let { zoneDecision(pkg, it.zone.id, fresh = true) }
        updateMask(pkg, detectors.zonesFor(pkg), snapshot)
        if (updateFeedShield(pkg, snapshot)) return
        if (match == null || decision == null) {
            throttle.onZoneLeft()
            if (tickJob != currentCoroutineContext()[Job]) tickJob?.cancel()
            return
        }

        if (decision is Decision.Allowed) {
            limitWarnings.showIfNeeded(pkg, decision, match.zone.id)
            // Zone permise (quota non atteint, ou autorisée) : on revient vérifier régulièrement,
            // au plus tard quand le quota sera atteint.
            scheduleTick(decision.remainingToday)
            return
        }
        decision as Decision.Blocked
        if (tickJob != currentCoroutineContext()[Job]) tickJob?.cancel()
        usageRecorder.onSnapshot(pkg, null) // zone inaccessible : plus rien à compter

        when (throttle.onZoneDetected(now)) {
            ZoneExitThrottle.Action.WAIT -> {
                if (ZoneExitPlanner.plan(match.zone, snapshot.nodes) is ExitStep.ScrollForward) {
                    scheduleTick(Duration.ofSeconds(1))
                }
            }
            ZoneExitThrottle.Action.EXIT -> {
                val step = ZoneExitPlanner.plan(match.zone, snapshot.nodes, throttle.attempts - 1)
                val accepted = exit(step, snapshot.infos)
                showToast(match.zone.label, decision.reason)
                withContext(Dispatchers.Default) { coordinator.recordZoneRedirect(pkg, match.zone.id) }
                if (step is ExitStep.ScrollForward) {
                    if (accepted) throttle.onExitProgress()
                    scheduleTick(Duration.ofSeconds(1))
                }
            }
            ZoneExitThrottle.Action.ESCALATE -> {
                throttle.onZoneLeft()
                onEscalate(pkg, decision)
            }
        }
    }

    private fun scheduleTick(remaining: Duration?) {
        val delayMs = (remaining?.toMillis() ?: TICK_MS).coerceIn(MIN_TICK_MS, TICK_MS)
        tickJob?.cancel()
        tickJob = scope.launch {
            delay(delayMs)
            try {
                check()
            } finally {
                if (tickJob == currentCoroutineContext()[Job]) tickJob = null
            }
        }
    }

    /** Décision du moteur pour une zone ; réutilisée quelques secondes sauf si [fresh]. */
    private suspend fun zoneDecision(pkg: String, zone: String, fresh: Boolean = false): Decision {
        val key = "$pkg/$zone"
        val nowMs = SystemClock.elapsedRealtime()
        zoneDecisions[key]?.let { (decision, atMs) -> if (!fresh && nowMs - atMs < DECISION_TTL_MS) return decision }
        return withContext(Dispatchers.Default) { coordinator.evaluateZone(pkg, zone) }
            .also { zoneDecisions[key] = it to nowMs }
    }

    /** Masque les points d'entrée visibles des zones inaccessibles maintenant. */
    private suspend fun updateMask(pkg: String, zones: List<ZoneDefinition>, snapshot: AccessibilitySnapshot) {
        val rects = zones.filter { it.hide.isNotEmpty() && zoneDecision(pkg, it.id) is Decision.Blocked }
            .flatMap { zone -> ZoneMatcher.entryPoints(zone, snapshot.nodes).map { it to zone.hideColor } }
            .map { (index, color) -> Rect().apply { snapshot.infos[index].getBoundsInScreen(this) } to color }
            .filter { !it.first.isEmpty }
        mask.show(rects)
        maskShown = rects.isNotEmpty()
    }

    private fun hideMask() {
        feedShield.clear()
        if (maskShown) mask.clear()
        maskShown = false
    }

    private suspend fun updateFeedShield(pkg: String, snapshot: AccessibilitySnapshot): Boolean {
        val isHome = pkg == InstagramFeedPolicy.PACKAGE &&
            InstagramFeedPolicy.classify(snapshot.nodes) == InstagramFeedPolicy.Surface.RECOMMENDED
        val hasReels = detectorStore.detectors.value.zonesFor(pkg).any { it.id == "reels" }
        if (!isHome || !hasReels || zoneDecision(pkg, "reels") !is Decision.Blocked) {
            feedShield.clear()
            return false
        }
        if (foregroundPackage != pkg) return true
        fun bounds(id: String): Rect? {
            val index = snapshot.nodes.indexOfFirst { it.viewId == "$pkg:id/$id" }
            return if (index >= 0) Rect().apply { snapshot.infos[index].getBoundsInScreen(this) } else null
        }
        val viewport = bounds("sticky_header_list") ?: return true
        bounds("main_feed_action_bar")?.let { viewport.top = maxOf(viewport.top, it.bottom) }
        bounds("tab_bar")?.let { viewport.bottom = minOf(viewport.bottom, it.top) }
        val wasShown = feedShield.isShown
        feedShield.show(viewport)
        usageRecorder.onSnapshot(pkg, null)
        throttles[pkg]?.onZoneLeft()
        if (!wasShown && feedShield.isShown) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Instagram recommended feed shield shown")
            withContext(Dispatchers.Default) { coordinator.recordZoneRedirect(pkg, "reels") }
        }
        scheduleTick(null)
        return true
    }

    private fun openFollowingFeed() {
        if (followingJob?.isActive == true) return
        followingJob = scope.launch {
            fun click(node: AccessibilityNodeInfo?): Boolean {
                var target = node
                while (target != null && !target.isClickable) target = target.parent
                return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            fun root() = service.rootInActiveWindow?.takeIf {
                foregroundPackage == InstagramFeedPolicy.PACKAGE && it.packageName?.toString() == InstagramFeedPolicy.PACKAGE
            }
            val initial = root() ?: return@launch
            val menuId = "${InstagramFeedPolicy.PACKAGE}:id/text_title_chevron_container"
            // Si la barre est repliée, un appui sur Accueil la rend à nouveau accessible.
            if (initial.findAccessibilityNodeInfosByViewId(menuId).none { it.isVisibleToUser }) {
                click(initial.findAccessibilityNodeInfosByViewId("${InstagramFeedPolicy.PACKAGE}:id/feed_tab").firstOrNull { it.isVisibleToUser })
                delay(400)
            }
            if (click(root()?.findAccessibilityNodeInfosByViewId(menuId)?.firstOrNull { it.isVisibleToUser })) {
                repeat(5) {
                    delay(250)
                    val current = root() ?: return@launch
                    val choice = current.findAccessibilityNodeInfosByViewId("${InstagramFeedPolicy.PACKAGE}:id/context_menu_item_label")
                        .firstOrNull { it.isVisibleToUser && it.text?.toString()?.trim() in InstagramFeedPolicy.followingLabels }
                    if (choice != null && click(choice)) return@launch
                }
            }
            Toast.makeText(service, R.string.instagram_following_unavailable, Toast.LENGTH_LONG).show()
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

    private fun exit(step: ExitStep, infos: List<AccessibilityNodeInfo>): Boolean {
        if (step is ExitStep.ScrollForward) {
            // Ne pas cliquer le média et ne pas quitter Instagram : passer au contenu suivant.
            return infos.getOrNull(step.nodeIndex)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        }
        if (step is ExitStep.Click) {
            var node: AccessibilityNodeInfo? = infos.getOrNull(step.nodeIndex)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun showToast(zoneLabel: String, reason: BlockReason) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastToastAtMs < TOAST_INTERVAL_MS) return
        lastToastAtMs = nowMs
        val text = if (reason is BlockReason.DailyLimitReached) {
            service.getString(R.string.zone_quota_reached, zoneLabel, Formats.duration(reason.limit))
        } else {
            service.getString(R.string.zone_hidden, zoneLabel)
        }
        Toast.makeText(service, text, Toast.LENGTH_SHORT).show()
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
        const val TAG = "MobileManager"
        const val CHECK_DELAY_MS = 400L
        const val SLOWER_CHECK_DELAY_MS = 800L
        const val SLOWEST_CHECK_DELAY_MS = 1_200L
        const val SLOWER_AFTER = 5
        const val SLOWEST_AFTER = 10
        const val DECISION_TTL_MS = 5_000L
        const val TICK_MS = 5_000L
        const val MIN_TICK_MS = 1_000L
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

        /** Seulement les nœuds visibles portant l'un de ces identifiants complets (`pkg:id/nom`). */
        fun captureByIds(root: AccessibilityNodeInfo, viewIds: Set<String>): AccessibilitySnapshot {
            val nodes = ArrayList<UiNode>()
            val infos = ArrayList<AccessibilityNodeInfo>()
            for (viewId in viewIds) {
                for (node in root.findAccessibilityNodeInfosByViewId(viewId)) {
                    if (!node.isVisibleToUser) continue
                    nodes += UiNode(
                        viewId = node.viewIdResourceName,
                        text = node.text?.toString(),
                        desc = node.contentDescription?.toString(),
                        selected = node.isSelected,
                        clickable = node.isClickable,
                        className = node.className?.toString(),
                    )
                    infos += node
                }
            }
            return AccessibilitySnapshot(nodes, infos)
        }
    }
}
