package com.lucas.mobilemanager.ui.diagnostic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.diagnostic.MonitorHealth
import com.lucas.mobilemanager.diagnostic.ProtectionTestStore
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.enforcement.EnforcementCoordinator
import com.lucas.mobilemanager.lock.AppLock
import com.lucas.mobilemanager.permissions.PermissionChecker
import com.lucas.mobilemanager.zones.ZoneDetectorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ProbeTarget(val packageName: String, val zoneId: String, val label: String)

@HiltViewModel
class ProtectionTestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val store: ProtectionTestStore,
    val permissionChecker: PermissionChecker,
    health: MonitorHealth,
    private val appLock: AppLock,
    private val sessions: StrictSessionRepository,
    private val timeProvider: TimeProvider,
    private val coordinator: EnforcementCoordinator,
    detectors: ZoneDetectorStore,
) : ViewModel() {
    val run = store.run
    val reviewNeeded = store.reviewNeeded
    val connected = health.connected
    val mode = appLock.modeSettings
    val locked = sessions.observeCurrent().map { it?.isRunning(timeProvider.now()) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val targets = detectors.detectors.map { file ->
        withContext(Dispatchers.IO) {
            file.apps.flatMap { app ->
                if (context.packageManager.getLaunchIntentForPackage(app.packageName) == null) emptyList()
                else {
                    val label = runCatching { context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(app.packageName, 0)).toString() }.getOrDefault(app.packageName)
                    app.zones.map { ProbeTarget(app.packageName, it.id, "$label · ${it.label}") }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selected = MutableStateFlow(store.selectedPackage to store.selectedZone)
    val message = MutableStateFlow<Int?>(null)
    val zoneDecision = MutableStateFlow<Decision?>(null)
    val appBlocked = MutableStateFlow(false)

    fun select(target: ProbeTarget) {
        if (run.value?.active == true) return
        store.select(target.packageName, target.zoneId)
        selected.value = target.packageName to target.zoneId
        zoneDecision.value = null
        appBlocked.value = false
    }

    fun refreshDecision(target: ProbeTarget?) {
        viewModelScope.launch {
            val app = target?.let { withContext(Dispatchers.Default) { coordinator.evaluate(it.packageName) } }
            val zone = target?.let { withContext(Dispatchers.Default) { coordinator.evaluateZone(it.packageName, it.zoneId) } }
            if (target == null || selected.value == (target.packageName to target.zoneId)) {
                appBlocked.value = app is Decision.Blocked
                zoneDecision.value = zone
            }
        }
    }

    fun start(target: ProbeTarget, launch: () -> Unit) {
        viewModelScope.launch {
            if (!appLock.canModifyNow() || sessions.current()?.isRunning(timeProvider.now()) == true) {
                message.value = R.string.probe_locked
                return@launch
            }
            if (!connected.value) {
                message.value = R.string.probe_disconnected
                return@launch
            }
            store.start(target.packageName, target.zoneId)
            message.value = null
            launch()
        }
    }

    fun launchFailed() {
        store.finish()
        message.value = R.string.probe_launch_failed
    }
}
