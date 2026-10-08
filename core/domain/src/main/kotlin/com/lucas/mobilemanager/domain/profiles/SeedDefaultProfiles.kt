package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Crée les profils par défaut une seule fois. Un indicateur dans les préférences évite de
 * les recréer si l'utilisateur les supprime.
 */
class SeedDefaultProfiles @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke() {
        if (preferencesRepository.preferences.first().defaultProfilesSeeded) return
        DefaultProfiles.ALL.forEach { profileRepository.upsert(it) }
        preferencesRepository.setDefaultProfilesSeeded(true)
    }
}
