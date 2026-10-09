package com.lucas.mobilemanager.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.apps.InstalledApps
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.repository.ZoneUsageRepository
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.domain.zones.ZoneKey
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
import java.time.Duration
import javax.inject.Inject

/** Réglage d'une zone dans les règles par défaut. */
enum class ZoneSetting { BLOCKED, QUOTA, ALLOWED }

data class ZoneItem(
    val packageName: String,
    val appLabel: String,
    val zoneId: String,
    val zoneLabel: String,
    val setting: ZoneSetting,
    /** Quota quotidien en minutes (affiché aussi quand la zone n'en a pas encore, comme valeur proposée). */
    val quotaMinutes: Int,
    val usedToday: Duration,
)

data class ZoneRulesState(val zones: List<ZoneItem> = emptyList(), val locked: Boolean = false)

/** Zones connues (détecteurs), leur réglage dans les règles par défaut et le temps passé aujourd'hui. */
@HiltViewModel
class ZoneRulesViewModel @Inject constructor(
    detectorStore: ZoneDetectorStore,
    private val profileRepository: ProfileRepository,
    strictSessionRepository: StrictSessionRepository,
    zoneUsageRepository: ZoneUsageRepository,
    private val installedApps: InstalledApps,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val labels = MutableStateFlow<Map<String, String>>(emptyMap())

    val state: StateFlow<ZoneRulesState> = combine(
        detectorStore.detectors,
        profileRepository.observeGlobalRules(),
        strictSessionRepository.observeCurrent().map { it?.isRunning(timeProvider.now()) == true },
        labels,
        zoneUsageRepository.observeDay(timeProvider.localNow().toLocalDate()),
    ) { detectors, globalRules, locked, labels, usage ->
        loadLabels(detectors.packages, labels)
        val zones = detectors.apps.flatMap { app ->
            app.zones.map { zone ->
                val action = globalRules.firstOrNull { it.target == Target.Zone(app.packageName, zone.id) }?.action
                ZoneItem(
                    packageName = app.packageName,
                    appLabel = labels[app.packageName] ?: app.packageName,
                    zoneId = zone.id,
                    zoneLabel = zone.label,
                    setting = when (action) {
                        RuleAction.Block -> ZoneSetting.BLOCKED
                        is RuleAction.Limit -> ZoneSetting.QUOTA
                        RuleAction.Allow, null -> ZoneSetting.ALLOWED
                    },
                    quotaMinutes = (action as? RuleAction.Limit)?.dailyLimit?.toMinutes()?.toInt() ?: DEFAULT_QUOTA_MINUTES,
                    usedToday = usage[ZoneKey(app.packageName, zone.id)] ?: Duration.ZERO,
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

    /**
     * Bloquée = règle par défaut `Block` sur la zone ; quota = `Limit` ; autorisée = plus de règle
     * (rien ne la restreint, sauf un profil).
     */
    fun setSetting(item: ZoneItem, setting: ZoneSetting, quotaMinutes: Int = item.quotaMinutes) {
        if (state.value.locked) return
        viewModelScope.launch {
            val target = Target.Zone(item.packageName, item.zoneId)
            val others = profileRepository.observeGlobalRules().first().filterNot { it.target == target }
            val rule = when (setting) {
                ZoneSetting.BLOCKED -> Rule(target, RuleAction.Block)
                ZoneSetting.QUOTA -> Rule(target, RuleAction.Limit(Duration.ofMinutes(quotaMinutes.toLong())))
                ZoneSetting.ALLOWED -> null
            }
            profileRepository.replaceGlobalRules(others + listOfNotNull(rule))
        }
    }

    private companion object {
        const val DEFAULT_QUOTA_MINUTES = 30
    }
}
