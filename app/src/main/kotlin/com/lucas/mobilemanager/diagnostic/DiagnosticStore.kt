package com.lucas.mobilemanager.diagnostic

import com.lucas.mobilemanager.domain.zones.UiNode
import com.lucas.mobilemanager.domain.zones.ZoneMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Capture de l'arbre d'accessibilité d'une fenêtre, faite à la demande. */
data class HierarchyCapture(
    val packageName: String,
    val nodes: List<UiNode>,
    val match: ZoneMatch?,
    val capturedAt: Instant,
)

/**
 * État du mode diagnostic, **en mémoire uniquement** : une capture peut contenir des textes
 * affichés à l'écran, elle n'est jamais enregistrée sur le disque.
 */
@Singleton
class DiagnosticStore @Inject constructor() {

    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _capture = MutableStateFlow<HierarchyCapture?>(null)
    val capture: StateFlow<HierarchyCapture?> = _capture.asStateFlow()

    fun setOverlayEnabled(enabled: Boolean) {
        _overlayEnabled.value = enabled
    }

    fun store(capture: HierarchyCapture) {
        _capture.value = capture
    }

    fun clear() {
        _capture.value = null
    }
}
