package com.lucas.mobilemanager.diagnostic

import android.content.Context
import android.os.SystemClock
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.zones.ProtectionProbe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ProtectionTestRun(
    val id: Long,
    val packageName: String,
    val zoneId: String,
    val active: Boolean = true,
    val probe: ProtectionProbe = ProtectionProbe(),
)

/** Seuls le choix de cible et l'installation examinée sont persistés. Preuves en mémoire. */
@Singleton
class ProtectionTestStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val preferences = context.getSharedPreferences("protection_test", Context.MODE_PRIVATE)
    private val installation = context.packageManager.getPackageInfo(context.packageName, 0).let {
        "${it.longVersionCode}:${it.lastUpdateTime}"
    }
    private val _reviewNeeded = MutableStateFlow(preferences.getString("reviewed", null) != installation)
    val reviewNeeded = _reviewNeeded.asStateFlow()
    private val _run = MutableStateFlow<ProtectionTestRun?>(null)
    val run = _run.asStateFlow()
    val selectedPackage get() = preferences.getString("package", null)
    val selectedZone get() = preferences.getString("zone", null)

    fun select(packageName: String, zoneId: String) {
        preferences.edit().putString("package", packageName).putString("zone", zoneId).apply()
    }

    fun start(packageName: String, zoneId: String) {
        select(packageName, zoneId)
        val id = SystemClock.elapsedRealtimeNanos()
        _run.value = ProtectionTestRun(id, packageName, zoneId)
        scope.launch {
            delay(60_000)
            finish(id)
        }
    }

    fun finish(id: Long? = _run.value?.id) {
        _run.update { if (it?.id == id) it?.copy(active = false) else it }
    }

    fun acknowledgeReview() {
        preferences.edit().putString("reviewed", installation).apply()
        _reviewNeeded.value = false
    }

    fun update(id: Long, transform: (ProtectionProbe) -> ProtectionProbe) {
        _run.update { if (it?.id == id && it.active) it.copy(probe = transform(it.probe)) else it }
    }

    fun event(packageName: String?) {
        val current = _run.value ?: return
        if (current.packageName == packageName) update(current.id) { it.copy(events = it.events + 1) }
    }

    fun detection(packageName: String, zoneId: String?, blocked: Boolean = false, action: Boolean = false) {
        val current = _run.value ?: return
        if (current.packageName != packageName || current.zoneId != zoneId) return
        update(current.id) { it.copy(zoneSeen = true, restrictionSeen = it.restrictionSeen || blocked,
            actionAccepted = it.actionAccepted || (blocked && action)) }
    }

    fun snapshot(packageName: String, nodes: Int, ids: Int, zoneId: String?) {
        val current = _run.value ?: return
        if (current.packageName == packageName) {
            update(current.id) { it.snapshot(nodes, ids, zoneId == current.zoneId) }
        }
    }

    fun fallback(packageName: String) {
        val current = _run.value ?: return
        if (current.packageName == packageName) update(current.id) { it.copy(fallbackUsed = true) }
    }

    fun entryMask(packageName: String, zoneId: String) {
        val current = _run.value ?: return
        if (current.packageName == packageName && current.zoneId == zoneId) {
            update(current.id) { it.copy(entryMasked = true) }
        }
    }

    fun confirmOverlay(confirmed: Boolean) {
        _run.update { it?.copy(probe = it.probe.copy(overlayConfirmed = confirmed)) }
    }

    fun confirmProtection(confirmed: Boolean) {
        _run.update { it?.copy(probe = it.probe.copy(protectionConfirmed = confirmed)) }
    }
}
