package com.lucas.mobilemanager.ui.home

import androidx.lifecycle.ViewModel
import com.lucas.mobilemanager.domain.onboarding.OnboardingState
import com.lucas.mobilemanager.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    private val _permissions = MutableStateFlow(OnboardingState.from(permissionChecker.currentStatuses()))
    val permissions: StateFlow<OnboardingState> = _permissions.asStateFlow()

    fun refresh() {
        _permissions.value = OnboardingState.from(permissionChecker.currentStatuses())
    }
}
