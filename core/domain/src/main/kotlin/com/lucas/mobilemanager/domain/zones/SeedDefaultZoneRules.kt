package com.lucas.mobilemanager.domain.zones

import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Zones bloquées par défaut (cahier des charges) : Reels Instagram et Shorts YouTube. */
object DefaultZoneRules {
    val ALL = listOf(
        Rule(Target.Zone("com.instagram.android", "reels"), RuleAction.Block),
        Rule(Target.Zone("com.google.android.youtube", "shorts"), RuleAction.Block),
    )
}

/**
 * Apps bloquées par défaut (règles par défaut, modifiables avec le code du proche) : TikTok,
 * entièrement fait de défilement. Remplace l'ancien régime anti-doomscrolling.
 */
object DefaultAppBlocks {
    val TIKTOK = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    val ALL = TIKTOK.map { Rule(Target.App(it), RuleAction.Block) }
}

/** Ajoute une seule fois le blocage de TikTok aux règles par défaut, sauf si l'app y est déjà réglée. */
class SeedDefaultAppBlocks @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke() {
        if (preferencesRepository.preferences.first().defaultAppBlocksSeeded) return
        val current = profileRepository.observeGlobalRules().first()
        val missing = DefaultAppBlocks.ALL.filter { rule -> current.none { it.target == rule.target } }
        if (missing.isNotEmpty()) profileRepository.replaceGlobalRules(current + missing)
        preferencesRepository.setDefaultAppBlocksSeeded(true)
        preferencesRepository.clearLegacyScrollGuardSettings()
    }
}

/**
 * Ajoute une seule fois les zones bloquées par défaut aux règles par défaut, sans toucher aux
 * règles existantes ni à une zone déjà réglée par l'utilisateur.
 */
class SeedDefaultZoneRules @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke() {
        if (preferencesRepository.preferences.first().defaultZoneRulesSeeded) return
        val current = profileRepository.observeGlobalRules().first()
        val missing = DefaultZoneRules.ALL.filter { rule -> current.none { it.target == rule.target } }
        if (missing.isNotEmpty()) profileRepository.replaceGlobalRules(current + missing)
        preferencesRepository.setDefaultZoneRulesSeeded(true)
    }
}
