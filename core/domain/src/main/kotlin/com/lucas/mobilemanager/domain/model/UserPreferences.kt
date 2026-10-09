package com.lucas.mobilemanager.domain.model

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val defaultProfilesSeeded: Boolean = false,
    val defaultScrollGuardsSeeded: Boolean = false,
    val defaultZoneRulesSeeded: Boolean = false,
)
