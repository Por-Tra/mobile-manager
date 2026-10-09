package com.lucas.mobilemanager.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.apps.InstalledApps
import com.lucas.mobilemanager.dashboard.DashboardLoader
import com.lucas.mobilemanager.domain.dashboard.DashboardSummary
import com.lucas.mobilemanager.domain.dashboard.Period
import com.lucas.mobilemanager.domain.dashboard.ZoneKey
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import com.lucas.mobilemanager.zones.ZoneDetectorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val period: Period = Period.TODAY,
    val summary: DashboardSummary? = null,
    val loading: Boolean = true,
    val goalMinutes: Int? = null,
    val appLabels: Map<String, String> = emptyMap(),
    val zoneLabels: Map<ZoneKey, String> = emptyMap(),
    val showAllApps: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val loader: DashboardLoader,
    private val preferencesRepository: UserPreferencesRepository,
    private val installedApps: InstalledApps,
    private val zoneDetectorStore: ZoneDetectorStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.summary == null) }
            val summary = loader.load(state.value.period)
            val goal = preferencesRepository.preferences.first().screenTimeGoalMinutes
            val packages = summary.apps.map { it.packageName } +
                (summary.zones.timeByZone.keys + summary.zones.exitsByZone.keys).map { it.packageName }
            val labels = packages.distinct().associateWith { installedApps.label(it) ?: it }
            val zoneLabels = zoneDetectorStore.detectors.value.apps
                .flatMap { app -> app.zones.map { ZoneKey(app.packageName, it.id) to it.label } }
                .toMap()
            _state.update {
                it.copy(summary = summary, loading = false, goalMinutes = goal, appLabels = labels, zoneLabels = zoneLabels)
            }
        }
    }

    fun setPeriod(period: Period) {
        if (period == state.value.period) return
        _state.update { it.copy(period = period, showAllApps = false) }
        refresh()
    }

    fun setGoal(minutes: Int?) {
        viewModelScope.launch {
            preferencesRepository.setScreenTimeGoal(minutes)
            refresh()
        }
    }

    fun toggleShowAllApps() = _state.update { it.copy(showAllApps = !it.showAllApps) }
}
