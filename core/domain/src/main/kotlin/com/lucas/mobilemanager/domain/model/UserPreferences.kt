package com.lucas.mobilemanager.domain.model

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val defaultProfilesSeeded: Boolean = false,
    val defaultAppBlocksSeeded: Boolean = false,
    val defaultZoneRulesSeeded: Boolean = false,
    /** Objectif de temps d'écran quotidien (tableau de bord), `null` si aucun. */
    val screenTimeGoalMinutes: Int? = null,
    val sharedBudgetEnabled: Boolean = false,
    val sharedBudgetMinutes: Int = 45,
    val sharedBudgetPackages: Set<String> = emptySet(),
    val openingPauseEnabled: Boolean = false,
    val openingPauseSeconds: Int = 5,
    val openingPausePackages: Set<String> = emptySet(),
    val limitWarningsEnabled: Boolean = true,
)
