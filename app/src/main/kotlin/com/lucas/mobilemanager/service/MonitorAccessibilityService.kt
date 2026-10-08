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
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.lucas.mobilemanager.block.BlockActivity
import com.lucas.mobilemanager.block.BlockScreenArgs
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.domain.foreground.ForegroundTracker
import com.lucas.mobilemanager.domain.foreground.HomePanels
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.rules.EnforcementPolicy
import com.lucas.mobilemanager.enforcement.EnforcementCoordinator
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
import javax.inject.Inject

/**
 * Source de vérité de l'app au premier plan. Ne lit que le nom du package de la fenêtre,
 * jamais son contenu. Délègue la décision à [EnforcementCoordinator] et applique le blocage.
 */
@AndroidEntryPoint
class MonitorAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: EnforcementCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tracker: ForegroundTracker
    private var watchJob: Job? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMs = 0L

    /** Fenêtres qui se superposent à l'app sans la remplacer (volet de notifications, clavier). */
    private var overlayPackages: Set<String> = emptySet()

    /** Cache : une classe de fenêtre est-elle une activité de son package ? */
    private val activityCache = HashMap<ComponentName, Boolean>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> watchJob?.cancel()
                Intent.ACTION_SCREEN_ON -> tracker.current?.takeIf { it != packageName }?.let(::watch)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        tracker = ForegroundTracker(selfPackage = packageName)
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
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
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
            ForegroundTracker.Change.Self -> watchJob?.cancel()
            is ForegroundTracker.Change.App -> watch(change.packageName)
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
        // Une app qui s'ouvre émet souvent plusieurs événements de fenêtre : un seul blocage compte.
        val nowMs = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage && nowMs - lastBlockedAtMs < DUPLICATE_BLOCK_WINDOW_MS) return
        lastBlockedPackage = pkg
        lastBlockedAtMs = nowMs
        startActivity(
            BlockScreenArgs(pkg, decision.reason, decision.until)
                .toIntent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        scope.launch(Dispatchers.Default) { coordinator.recordBlock(pkg, decision) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MobileManager"
        const val SYSTEM_UI = "com.android.systemui"
        const val DUPLICATE_BLOCK_WINDOW_MS = 2_000L
    }
}
