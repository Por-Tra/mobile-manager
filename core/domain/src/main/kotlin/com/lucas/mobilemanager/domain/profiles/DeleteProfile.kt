package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.repository.ManualActivationRepository
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Supprime un profil, et son activation manuelle s'il était activé à la main. */
class DeleteProfile @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val manualActivationRepository: ManualActivationRepository,
) {
    suspend operator fun invoke(profileId: Long) {
        if (manualActivationRepository.activation.first()?.profileId == profileId) {
            manualActivationRepository.clear()
        }
        profileRepository.delete(profileId)
    }
}
