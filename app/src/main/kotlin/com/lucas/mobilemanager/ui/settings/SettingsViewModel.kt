package com.lucas.mobilemanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    /** Crée (ou réinitialise) le profil de test, pour vérifier le blocage sans attendre les bonnes heures. */
    fun createTestProfile() {
        viewModelScope.launch {
            val existingId = findTestProfileId() ?: 0L
            profileRepository.upsert(TEST_PROFILE.copy(id = existingId))
        }
    }

    fun deleteTestProfile() {
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
