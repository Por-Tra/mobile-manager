package com.lucas.mobilemanager.domain.model

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val defaultProfilesSeeded: Boolean = false,
)
