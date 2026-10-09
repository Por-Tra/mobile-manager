package com.lucas.mobilemanager.domain.doomscroll

import com.lucas.mobilemanager.domain.repository.ScrollGuardRepository
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Crée les réglages anti-doomscrolling par défaut une seule fois (supprimables ensuite). */
class SeedDefaultScrollGuards @Inject constructor(
    private val guardRepository: ScrollGuardRepository,
    private val preferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke() {
        if (preferencesRepository.preferences.first().defaultScrollGuardsSeeded) return
        DefaultScrollGuards.ALL.forEach { guardRepository.upsert(it) }
        preferencesRepository.setDefaultScrollGuardsSeeded(true)
    }
}
