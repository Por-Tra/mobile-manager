package com.lucas.mobilemanager.ui.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.apps.InstalledApp
import com.lucas.mobilemanager.apps.InstalledApps
import com.lucas.mobilemanager.domain.profiles.DefaultProfiles
import com.lucas.mobilemanager.domain.session.StrictSessionManager
import com.lucas.mobilemanager.domain.session.StrictSessionManager.StartResult
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy.ValidationError
import com.lucas.mobilemanager.domain.time.LocalTimes
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.session.StrictSessionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/** Durée choisie : un préréglage, ou « jusqu'à » une heure locale. */
sealed interface SessionLength {
    data class Preset(val duration: Duration) : SessionLength
    data class Until(val time: LocalTime) : SessionLength

    companion object {
        val PRESETS = listOf(25L, 50L, 90L, 120L, 180L).map { Preset(Duration.ofMinutes(it)) }
    }
}

data class StartSessionState(
    val apps: List<InstalledApp> = emptyList(),
    val loadingApps: Boolean = true,
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val length: SessionLength = SessionLength.PRESETS.first(),
    val reflectionDelayMinutes: Int = 5,
    val errors: Set<ValidationError> = emptySet(),
    val alreadyRunning: Boolean = false,
    val confirming: Boolean = false,
    val started: Boolean = false,
)

@HiltViewModel
class StartSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installedApps: InstalledApps,
    private val manager: StrictSessionManager,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(StartSessionState())
    val state: StateFlow<StartSessionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = installedApps.blockable()
            val distracting = DefaultProfiles.DISTRACTING_APPS.toSet()
            // Les apps distrayantes installées sont présélectionnées et affichées en tête.
            _state.update {
                it.copy(
                    apps = apps.sortedByDescending { app -> app.packageName in distracting },
                    selected = apps.map(InstalledApp::packageName).filter(distracting::contains).toSet(),
                    loadingApps = false,
                )
            }
        }
    }

    fun endsAt(length: SessionLength = state.value.length): Instant {
        val now = timeProvider.localNow()
        return when (length) {
            is SessionLength.Preset -> now.plus(length.duration).toInstant()
            is SessionLength.Until -> LocalTimes.nextOccurrence(length.time, now).toInstant()
        }
    }

    fun setLength(length: SessionLength) = _state.update { it.copy(length = length, errors = emptySet()) }

    fun setReflectionDelay(minutes: Int) = _state.update { it.copy(reflectionDelayMinutes = minutes) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun toggle(packageName: String) = _state.update {
        val selected = if (packageName in it.selected) it.selected - packageName else it.selected + packageName
        it.copy(selected = selected, errors = emptySet())
    }

    fun askConfirmation() = _state.update { it.copy(confirming = true) }

    fun dismissConfirmation() = _state.update { it.copy(confirming = false) }

    fun start() {
        val current = state.value
        viewModelScope.launch {
            val result = manager.start(
                endsAt = endsAt(current.length),
                blockedPackages = current.selected,
                reflectionDelay = Duration.ofMinutes(current.reflectionDelayMinutes.toLong()),
            )
            _state.update {
                when (result) {
                    is StartResult.Started -> {
                        StrictSessionService.start(context)
                        it.copy(confirming = false, started = true)
                    }
                    is StartResult.Invalid -> it.copy(confirming = false, errors = result.errors)
                    StartResult.AlreadyRunning -> it.copy(confirming = false, alreadyRunning = true)
                }
            }
        }
    }
}
