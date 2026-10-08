package com.lucas.mobilemanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.onboarding.OnboardingState
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.time.ScheduleEvaluator
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileItem(
    val profile: Profile,
    /** Le profil s'applique en ce moment (activé et dans une de ses plages). */
    val activeNow: Boolean,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val profileRepository: ProfileRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _permissions = MutableStateFlow(OnboardingState.from(permissionChecker.currentStatuses()))
    val permissions: StateFlow<OnboardingState> = _permissions.asStateFlow()

    /** Incrémenté à chaque retour sur l'écran pour recalculer « actif maintenant ». */
    private val refreshTick = MutableStateFlow(0)

    val profiles: StateFlow<List<ProfileItem>> = combine(
        profileRepository.observeProfiles(),
        refreshTick,
    ) { profiles, _ ->
        val now = timeProvider.localNow()
        profiles.map { profile ->
            ProfileItem(
                profile = profile,
                activeNow = profile.enabled &&
                    profile.autoActivation &&
                    ScheduleEvaluator.isAnyActive(profile.schedules, now),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        _permissions.value = OnboardingState.from(permissionChecker.currentStatuses())
        refreshTick.value++
    }

    fun setEnabled(profileId: Long, enabled: Boolean) {
        viewModelScope.launch { profileRepository.setEnabled(profileId, enabled) }
    }
}
