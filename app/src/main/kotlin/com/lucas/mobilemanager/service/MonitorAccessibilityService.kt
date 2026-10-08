package com.lucas.mobilemanager.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.lucas.mobilemanager.block.BlockActivity
import com.lucas.mobilemanager.block.BlockScreenArgs
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
    private var currentPackage: String? = null
    private var watchJob: Job? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMs = 0L

    /** Fenêtres qui se superposent à l'app sans la remplacer (volet de notifications, clavier). */
    private var overlayPackages: Set<String> = emptySet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> watchJob?.cancel()
                Intent.ACTION_SCREEN_ON -> currentPackage?.let(::watch)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
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
        val pkg = event.packageName?.toString() ?: return
        // Les superpositions (volet, clavier) ne remplacent pas l'app courante.
        if (pkg in overlayPackages || pkg == currentPackage) return
        currentPackage = pkg
        if (pkg == packageName) {
            // Nos propres écrans (dont l'écran de blocage) : rien à surveiller.
            watchJob?.cancel()
            return
        }
        watch(pkg)
    }

    /** Évalue [pkg] maintenant, puis régulièrement tant qu'il reste au premier plan. */
    private fun watch(pkg: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            while (isActive) {
                val decision = withContext(Dispatchers.Default) { coordinator.evaluate(pkg) }
                if (currentPackage != pkg) return@launch
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
        currentPackage = null
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
        const val SYSTEM_UI = "com.android.systemui"
        const val DUPLICATE_BLOCK_WINDOW_MS = 2_000L
    }
}
