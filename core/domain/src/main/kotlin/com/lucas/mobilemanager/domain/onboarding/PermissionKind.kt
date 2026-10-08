package com.lucas.mobilemanager.domain.onboarding

/** Importance d'une permission pour le fonctionnement de l'app. */
enum class PermissionRequirement {
    /** Sans elle, la surveillance ne peut pas fonctionner de façon fiable. */
    REQUIRED,

    /** Améliore l'expérience (notification persistante, superposition) mais n'est pas bloquante. */
    RECOMMENDED,

    /** Nécessaire uniquement pour le mode verrouillage strict. */
    STRICT_MODE,
}

/** Permissions guidées par l'onboarding, dans l'ordre d'affichage. */
enum class PermissionKind(val requirement: PermissionRequirement) {
    ACCESSIBILITY(PermissionRequirement.REQUIRED),
    USAGE_STATS(PermissionRequirement.REQUIRED),
    BATTERY_OPTIMIZATION(PermissionRequirement.REQUIRED),
    OVERLAY(PermissionRequirement.RECOMMENDED),
    NOTIFICATIONS(PermissionRequirement.RECOMMENDED),
    DEVICE_ADMIN(PermissionRequirement.STRICT_MODE),
}

enum class PermissionStatus {
    GRANTED,
    MISSING,

    /** La permission n'existe pas sur cette version d'Android (ex. notifications avant API 33). */
    NOT_APPLICABLE,
}
