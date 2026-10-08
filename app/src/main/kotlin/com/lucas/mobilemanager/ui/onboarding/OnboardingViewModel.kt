package com.lucas.mobilemanager.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.domain.onboarding.OnboardingState
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import com.lucas.mobilemanager.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState.from(permissionChecker.currentStatuses()))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    /** À appeler à chaque retour sur l'écran : l'utilisateur revient des Paramètres système. */
    fun refresh() {
        _state.value = OnboardingState.from(permissionChecker.currentStatuses())
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
            onDone()
        }
    }
}
