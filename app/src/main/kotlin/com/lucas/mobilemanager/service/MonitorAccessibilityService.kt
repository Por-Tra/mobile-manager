package com.lucas.mobilemanager.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.lucas.mobilemanager.block.BlockActivity
import com.lucas.mobilemanager.block.BlockScreenArgs
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.MainActivity
import com.lucas.mobilemanager.diagnostic.DiagnosticOverlay
import com.lucas.mobilemanager.diagnostic.DiagnosticStore
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.zones.ZoneDetectorStore
import com.lucas.mobilemanager.zones.ZoneEnforcer
import com.lucas.mobilemanager.zones.ZoneUsageRecorder
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.foreground.ForegroundTracker
import com.lucas.mobilemanager.domain.foreground.HomePanels
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.session.ProtectedScreenDetector
import com.lucas.mobilemanager.domain.rules.EnforcementPolicy
import com.lucas.mobilemanager.enforcement.EnforcementCoordinator
import com.lucas.mobilemanager.ui.format.Formats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject

/**
 * Source de vérité de l'app au premier plan. Ne lit que le nom du package de la fenêtre,
 * jamais son contenu. Délègue la décision à [EnforcementCoordinator] et applique le blocage.
 */
@AndroidEntryPoint
class MonitorAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: EnforcementCoordinator
    @Inject lateinit var zoneDetectorStore: ZoneDetectorStore
    @Inject lateinit var diagnosticStore: DiagnosticStore
    @Inject lateinit var timeProvider: TimeProvider
    @Inject lateinit var zoneUsageRecorder: ZoneUsageRecorder

    private lateinit var zoneEnforcer: ZoneEnforcer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tracker: ForegroundTracker
    private var watchJob: Job? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMs = 0L
    private var protectedCheckJob: Job? = null
    private var lastProtectedToastAtMs = 0L

    /** Fenêtres qui se superposent à l'app sans la remplacer (volet de notifications, clavier). */
    private var overlayPackages: Set<String> = emptySet()

    /** Cache : une classe de fenêtre est-elle une activité de son package ? */
    private val activityCache = HashMap<ComponentName, Boolean>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    watchJob?.cancel()
                    zoneEnforcer.onForegroundChanged()
                }
                Intent.ACTION_SCREEN_ON -> tracker.current?.takeIf { it != packageName }?.let(::watch)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tracker = ForegroundTracker(selfPackage = packageName)
        zoneEnforcer = ZoneEnforcer(this, scope, coordinator, zoneDetectorStore, timeProvider, zoneUsageRecorder, ::block)
        scope.launch {
            diagnosticStore.overlayEnabled.collect { enabled -> setDiagnosticOverlay(enabled) }
        }
        overlayPackages = buildSet {
            add(SYSTEM_UI)
            getSystemService(InputMethodManager::class.java)?.inputMethodList?.forEach { add(it.packageName) }
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackage = event.packageName?.toString()
        if (eventPackage != null && eventPackage in ProtectedScreenDetector.PROTECTED_PACKAGES) {
            scheduleProtectedScreenCheck(eventPackage)
        }
        if (eventPackage != null &&
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        ) {
            zoneEnforcer.onEvent(eventPackage)
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = eventPackage
        val className = event.className?.toString()
        // Un écran d'app = une activité, hors panneaux intégrés au bureau (flux Google…).
        val isAppScreen = pkg != null && isActivity(pkg, className) && !HomePanels.isHomePanel(pkg, className)
        if (BuildConfig.DEBUG) Log.d(TAG, "window $pkg/$className appScreen=$isAppScreen")
        val change = tracker.onWindowStateChanged(
            packageName = pkg,
            isActivity = isAppScreen,
            overlayPackages = overlayPackages,
        )
        when (change) {
            ForegroundTracker.Change.None -> Unit
            // Nos propres écrans (dont l'écran de blocage) : rien à surveiller.
            ForegroundTracker.Change.Self -> {
                watchJob?.cancel()
                zoneEnforcer.onForegroundChanged()
            }
            is ForegroundTracker.Change.App -> {
                zoneEnforcer.onForegroundChanged()
                watch(change.packageName)
            }
        }
    }

    /**
     * Vrai si la fenêtre est une activité de [pkg]. Le flux Google du bureau, les widgets et les
     * popups ouvrent des fenêtres qui n'en sont pas : ils ne doivent pas compter comme une app ouverte.
     */
    private fun isActivity(pkg: String, className: String?): Boolean {
        val component = ComponentName(pkg, className ?: return false)
        return activityCache.getOrPut(component) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getActivityInfo(component, 0)
                }
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /**
     * Pendant une session, referme les écrans des Paramètres / de désinstallation qui affichent
     * Mobile Manager. Regroupe les rafales d'événements de contenu en une seule vérification.
     */
    private fun scheduleProtectedScreenCheck(pkg: String) {
        val session = coordinator.runningSession() ?: return
        protectedCheckJob?.cancel()
        protectedCheckJob = scope.launch {
            delay(PROTECTED_CHECK_DEBOUNCE_MS)
            val root = rootInActiveWindow ?: return@launch
            if (root.packageName?.toString() != pkg) return@launch
            val labels = setOf(getString(R.string.app_name), getString(R.string.accessibility_service_label))
            if (ProtectedScreenDetector.shouldClose(pkg, visibleTexts(root), labels, sessionRunning = true)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                showProtectedToast(session)
            }
        }
    }

    /** Textes et descriptions visibles de l'écran (parcours borné de l'arbre d'accessibilité). */
    private fun visibleTexts(root: AccessibilityNodeInfo): Sequence<String> = sequence {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_SCANNED) {
            val node = queue.removeFirst()
            visited++
            node.text?.let { yield(it.toString()) }
            node.contentDescription?.let { yield(it.toString()) }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
    }

    private fun showProtectedToast(session: StrictSession) {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastProtectedToastAtMs < PROTECTED_TOAST_INTERVAL_MS) return
        lastProtectedToastAtMs = nowMs
        val endTime = Formats.TIME.format(session.endsAt.atZone(ZoneId.systemDefault()))
        Toast.makeText(this, getString(R.string.protected_screen_closed, endTime), Toast.LENGTH_SHORT).show()
    }

    /** Évalue [pkg] maintenant, puis régulièrement tant qu'il reste au premier plan. */
    private fun watch(pkg: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                val decision = withContext(Dispatchers.Default) { coordinator.evaluate(pkg) }
                if (tracker.current != pkg) return@launch
                when (decision) {
                    is Decision.Blocked -> {
                        block(pkg, decision)
                        return@launch
                    }
                    is Decision.Allowed -> {
                        if (coordinator.isWhitelisted(pkg)) return@launch
                        val delay = EnforcementPolicy.nextCheckDelay(decision) ?: return@launch
                        delay(delay.toMillis())
                    }
                }
            }
        }
    }

    /**
     * Affiche l'écran de blocage par-dessus l'app. Pas de GLOBAL_ACTION_HOME ici : l'action est
     * asynchrone et l'accueil recouvrirait l'écran de blocage. C'est l'écran qui renvoie à l'accueil.
     */
    private fun block(pkg: String, decision: Decision.Blocked) {
        tracker.reset()
        // Toujours (ré)afficher l'écran : une app qui enchaîne deux écrans au démarrage
        // (ex. Messages et sa page de connexion) repasse sinon par-dessus le blocage.
        startActivity(
            BlockScreenArgs(pkg, decision.reason, decision.until)
                .toIntent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        // Mais un seul blocage compte dans le journal pour cette ouverture.
        val nowMs = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage && nowMs - lastBlockedAtMs < DUPLICATE_BLOCK_WINDOW_MS) return
        lastBlockedPackage = pkg
        lastBlockedAtMs = nowMs
        scope.launch(Dispatchers.Default) { coordinator.recordBlock(pkg, decision) }
    }

    /** Bandeau du mode diagnostic : zone détectée en direct + bouton « Capturer ». */
    private fun setDiagnosticOverlay(enabled: Boolean) {
        if (enabled) {
            val overlay = zoneEnforcer.overlay ?: DiagnosticOverlay(this, ::captureForDiagnostic).also {
                zoneEnforcer.overlay = it
            }
            overlay.show()
        } else {
            zoneEnforcer.overlay?.hide()
            zoneEnforcer.overlay = null
        }
    }

    private fun captureForDiagnostic() {
        val capture = zoneEnforcer.capture() ?: return
        diagnosticStore.store(capture)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_DIAGNOSTIC, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        zoneEnforcer.overlay?.hide()
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MobileManager"
        const val SYSTEM_UI = "com.android.systemui"
        const val DUPLICATE_BLOCK_WINDOW_MS = 2_000L
        const val PROTECTED_CHECK_DEBOUNCE_MS = 150L
        const val PROTECTED_TOAST_INTERVAL_MS = 3_000L
        const val MAX_NODES_SCANNED = 500
    }
}
