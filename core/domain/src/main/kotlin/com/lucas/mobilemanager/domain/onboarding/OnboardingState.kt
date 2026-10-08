package com.lucas.mobilemanager.domain.onboarding

data class PermissionItem(
    val kind: PermissionKind,
    val status: PermissionStatus,
) {
    val isSatisfied: Boolean
        get() = status != PermissionStatus.MISSING
}

/**
 * État de l'onboarding calculé à partir du statut de chaque permission.
 * Une permission absente de la map est considérée comme manquante.
 */
data class OnboardingState(val items: List<PermissionItem>) {

    /** Toutes les permissions indispensables à la surveillance sont accordées. */
    val canMonitor: Boolean
        get() = itemsWith(PermissionRequirement.REQUIRED).all { it.isSatisfied }

    /** Le mode strict demande en plus l'administration de l'appareil. */
    val strictModeAvailable: Boolean
        get() = canMonitor && itemsWith(PermissionRequirement.STRICT_MODE).all { it.isSatisfied }

    /** Nombre de permissions applicables et accordées / applicables. */
    val grantedCount: Int
        get() = items.count { it.status == PermissionStatus.GRANTED }

    val applicableCount: Int
        get() = items.count { it.status != PermissionStatus.NOT_APPLICABLE }

    val progress: Float
        get() = if (applicableCount == 0) 1f else grantedCount.toFloat() / applicableCount

    /** Prochaine permission à accorder, par ordre d'importance puis d'affichage. */
    val nextMissing: PermissionKind?
        get() = items
            .filter { it.status == PermissionStatus.MISSING }
            .minWithOrNull(compareBy({ it.kind.requirement.ordinal }, { it.kind.ordinal }))
            ?.kind

    private fun itemsWith(requirement: PermissionRequirement) =
        items.filter { it.kind.requirement == requirement }

    companion object {
        fun from(statuses: Map<PermissionKind, PermissionStatus>): OnboardingState =
            OnboardingState(
                PermissionKind.entries.map { kind ->
                    PermissionItem(kind, statuses[kind] ?: PermissionStatus.MISSING)
                },
            )
    }
}
