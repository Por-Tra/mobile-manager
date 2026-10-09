package com.lucas.mobilemanager.ui.doomscroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.apps.InstalledApp
import com.lucas.mobilemanager.apps.InstalledApps
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuard
import com.lucas.mobilemanager.domain.doomscroll.ScrollGuardSettings
import com.lucas.mobilemanager.domain.repository.ScrollGuardRepository
import com.lucas.mobilemanager.domain.repository.ScrollGuardSettingsRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

data class GuardItem(val guard: ScrollGuard, val label: String, val installed: Boolean)

data class ScrollGuardUiState(
    val settings: ScrollGuardSettings = ScrollGuardSettings(),
    val guards: List<GuardItem> = emptyList(),
    /** Session de concentration en cours : lecture seule. */
    val locked: Boolean = false,
)

@HiltViewModel
class ScrollGuardViewModel @Inject constructor(
    private val guardRepository: ScrollGuardRepository,
    private val settingsRepository: ScrollGuardSettingsRepository,
    strictSessionRepository: StrictSessionRepository,
    private val installedApps: InstalledApps,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val labels = MutableStateFlow<Map<String, String?>>(emptyMap())

    val state: StateFlow<ScrollGuardUiState> = combine(
        settingsRepository.settings,
        guardRepository.observeGuards(),
        strictSessionRepository.observeCurrent().map { it?.isRunning(timeProvider.now()) == true },
        labels,
    ) { settings, guards, locked, labels ->
        loadMissingLabels(guards.map { it.packageName }, labels)
        ScrollGuardUiState(
            settings = settings,
            guards = guards
                .map { GuardItem(it, labels[it.packageName] ?: it.packageName, labels[it.packageName] != null) }
                .sortedWith(compareBy({ !it.installed }, { it.label.lowercase() })),
            locked = locked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrollGuardUiState())

    /** Apps proposées dans le sélecteur. */
    private val _installed = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installed: StateFlow<List<InstalledApp>> = _installed

    private fun loadMissingLabels(packages: List<String>, known: Map<String, String?>) {
        val missing = packages.filterNot(known::containsKey)
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = missing.associateWith { installedApps.label(it) }
            labels.update { it + loaded }
        }
    }

    private fun editable(action: suspend () -> Unit) {
        if (state.value.locked) return
        viewModelScope.launch { action() }
    }

    fun setEnabled(enabled: Boolean) = editable { settingsRepository.update { it.copy(enabled = enabled) } }

    fun setSessionGap(gap: Duration) = editable { settingsRepository.update { it.copy(sessionGap = gap) } }

    fun setPauseDuration(duration: Duration) = editable { settingsRepository.update { it.copy(pauseDuration = duration) } }

    fun setGoal(goal: String) = editable { settingsRepository.update { it.copy(goal = goal.take(MAX_GOAL_LENGTH)) } }

    fun updateGuard(guard: ScrollGuard) = editable { guardRepository.upsert(guard) }

    fun removeGuard(packageName: String) = editable { guardRepository.delete(packageName) }

    fun loadInstalledApps() {
        if (_installed.value.isNotEmpty()) return
        viewModelScope.launch { _installed.value = installedApps.blockable() }
    }

    /** Nouvelle app surveillée : pause toutes les 15 min, sans blocage (comme les apps de messagerie). */
    fun toggleApp(app: InstalledApp) = editable {
        if (state.value.guards.any { it.guard.packageName == app.packageName }) {
            guardRepository.delete(app.packageName)
        } else {
            labels.update { it + (app.packageName to app.label) }
            guardRepository.upsert(ScrollGuard(app.packageName, pauseEvery = Duration.ofMinutes(15)))
        }
    }

    private companion object {
        const val MAX_GOAL_LENGTH = 80
    }
}
