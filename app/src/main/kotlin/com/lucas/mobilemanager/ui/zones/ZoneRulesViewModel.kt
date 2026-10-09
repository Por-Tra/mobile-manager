package com.lucas.mobilemanager.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.apps.InstalledApps
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.zones.ZoneDetectorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZoneItem(
    val packageName: String,
    val appLabel: String,
    val zoneId: String,
    val zoneLabel: String,
    val blocked: Boolean,
)

data class ZoneRulesState(val zones: List<ZoneItem> = emptyList(), val locked: Boolean = false)

/** Zones connues (détecteurs) et leur réglage dans les règles par défaut. */
@HiltViewModel
class ZoneRulesViewModel @Inject constructor(
    detectorStore: ZoneDetectorStore,
    private val profileRepository: ProfileRepository,
    strictSessionRepository: StrictSessionRepository,
    private val installedApps: InstalledApps,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())

    val state: StateFlow<ZoneRulesState> = combine(
        detectorStore.detectors,
        profileRepository.observeGlobalRules(),
        strictSessionRepository.observeCurrent().map { it?.isRunning(timeProvider.now()) == true },
        labels,
    ) { detectors, globalRules, locked, labels ->
        loadLabels(detectors.packages, labels)
        val zones = detectors.apps.flatMap { app ->
            app.zones.map { zone ->
                ZoneItem(
                    packageName = app.packageName,
                    appLabel = labels[app.packageName] ?: app.packageName,
                    zoneId = zone.id,
                    zoneLabel = zone.label,
                    blocked = globalRules.any {
                        it.target == Target.Zone(app.packageName, zone.id) && it.action == RuleAction.Block
                    },
                )
            }
        }
        ZoneRulesState(zones, locked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoneRulesState())

    private fun loadLabels(packages: Set<String>, known: Map<String, String>) {
        val missing = packages - known.keys
        if (missing.isEmpty()) return
        viewModelScope.launch {
            labels.update { current -> current + missing.associateWith { installedApps.label(it) ?: it } }
        }
    }

    /** Bloquer = règle par défaut `Block` sur la zone ; autoriser = plus de règle (rien ne la bloque). */
    fun setBlocked(item: ZoneItem, blocked: Boolean) {
        if (state.value.locked) return
        viewModelScope.launch {
            val target = Target.Zone(item.packageName, item.zoneId)
            val others = profileRepository.observeGlobalRules().first().filterNot { it.target == target }
            profileRepository.replaceGlobalRules(if (blocked) others + Rule(target, RuleAction.Block) else others)
        }
    }
}
