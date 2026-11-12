package com.lucas.mobilemanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.lock.AppLock
import com.lucas.mobilemanager.lock.LockStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val strictSessionRepository: StrictSessionRepository,
    private val timeProvider: TimeProvider,
    private val appLock: AppLock,
    private val preferencesRepository: com.lucas.mobilemanager.domain.repository.UserPreferencesRepository,
    private val installedApps: com.lucas.mobilemanager.apps.InstalledApps,
    val permissionChecker: com.lucas.mobilemanager.permissions.PermissionChecker,
    monitorHealth: com.lucas.mobilemanager.diagnostic.MonitorHealth,
) : ViewModel() {
    val preferences = preferencesRepository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val apps = kotlinx.coroutines.flow.MutableStateFlow<List<com.lucas.mobilemanager.apps.InstalledApp>>(emptyList())
    val monitorConnected = monitorHealth.connected
    val modeSettings = appLock.modeSettings

    init { viewModelScope.launch { apps.value = installedApps.blockable() } }

    fun saveBudget(enabled: Boolean, minutes: Int, packages: Set<String>) {
        viewModelScope.launch {
            if (!canEdit() || minutes !in 1..1440) return@launch
            preferencesRepository.setSharedBudget(enabled, minutes, packages)
        }
    }

    fun savePause(enabled: Boolean, seconds: Int, packages: Set<String>) {
        viewModelScope.launch {
            if (!canEdit() || seconds !in 3..30) return@launch
            preferencesRepository.setOpeningPause(enabled, seconds, packages)
        }
    }

    fun setWarnings(enabled: Boolean) {
        viewModelScope.launch {
            if (canEdit()) preferencesRepository.setLimitWarnings(enabled)
        }
    }

    private suspend fun canEdit(): Boolean =
        strictSessionRepository.current()?.isRunning(timeProvider.now()) != true && appLock.canModifyNow()

    suspend fun canUninstall(): Boolean = canEdit()

    val lockStatus: StateFlow<LockStatus?> = appLock.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Pendant une session de concentration, les outils qui modifient les règles sont désactivés. */
    val sessionRunning: StateFlow<Boolean> = strictSessionRepository.observeCurrent()
        .map { it?.isRunning(timeProvider.now()) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Crée (ou réinitialise) le profil de test, pour vérifier le blocage sans attendre les bonnes heures. */
    fun createTestProfile() {
        if (!appLock.canModifyNow()) return
        viewModelScope.launch {
            val existingId = findTestProfileId() ?: 0L
            profileRepository.upsert(TEST_PROFILE.copy(id = existingId))
        }
    }

    fun deleteTestProfile() {
        if (!appLock.canModifyNow()) return
        viewModelScope.launch { findTestProfileId()?.let { profileRepository.delete(it) } }
    }

    private suspend fun findTestProfileId(): Long? =
        profileRepository.observeProfiles().first().firstOrNull { it.name == TEST_PROFILE.name }?.id

    private companion object {
        val TEST_PROFILE = Profile(
            name = "Test",
            priority = 1000,
            schedules = listOf(Schedule(Schedule.EVERY_DAY, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT)),
            rules = listOf(
                Rule(Target.App("com.android.chrome"), RuleAction.Limit(Duration.ofMinutes(1))),
                Rule(Target.App("com.google.android.youtube"), RuleAction.Block),
            ),
        )
    }
}
