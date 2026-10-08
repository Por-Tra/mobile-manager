package com.lucas.mobilemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import com.lucas.mobilemanager.navigation.HomeRoute
import com.lucas.mobilemanager.navigation.OnboardingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    // Lu une seule fois : changer la destination de départ en cours de route casserait la navigation.
    private val _startDestination = MutableStateFlow<Any?>(null)
    val startDestination: StateFlow<Any?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            _startDestination.value = if (prefs.onboardingCompleted) HomeRoute else OnboardingRoute
        }
    }
}
