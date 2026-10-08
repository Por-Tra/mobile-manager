package com.lucas.mobilemanager.domain.rules

import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.time.ScheduleEvaluator
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

/** Origine d'une couche de règles, utilisée pour expliquer un blocage. */
sealed interface LayerSource {
    data object StrictSession : LayerSource
    data class ManualProfile(val profileId: Long, val name: String) : LayerSource
    data class ScheduledProfile(val profileId: Long, val name: String, val priority: Int) : LayerSource
    data object Global : LayerSource
}

data class PolicyLayer(
    val source: LayerSource,
    val rules: List<Rule>,
    /** Fin connue de la couche (fin de session, de plage, d'activation manuelle), sinon `null`. */
    val activeUntil: Instant?,
)

/** Couches actives, de la plus prioritaire à la moins prioritaire. */
data class ResolvedPolicy(val layers: List<PolicyLayer>)

/**
 * Détermine quelles règles s'appliquent maintenant et dans quel ordre de priorité :
 * 1. session stricte en cours ;
 * 2. profil activé manuellement ;
 * 3. profils planifiés actifs, par priorité décroissante puis id croissant ;
 * 4. règles globales.
 */
class ProfileResolver @Inject constructor() {

    fun resolve(
        profiles: List<Profile>,
        globalRules: List<Rule>,
        manualActivation: ManualActivation?,
        strictSession: StrictSession?,
        now: ZonedDateTime,
    ): ResolvedPolicy {
        val instant = now.toInstant()
        val layers = mutableListOf<PolicyLayer>()

        if (strictSession != null && strictSession.isRunning(instant)) {
            layers += PolicyLayer(
                source = LayerSource.StrictSession,
                rules = strictSession.blockedPackages.sorted().map { Rule(Target.App(it), RuleAction.Block) },
                activeUntil = strictSession.endsAt,
            )
        }

        val manualProfile = manualActivation
            ?.takeIf { it.until == null || instant.isBefore(it.until) }
            ?.let { activation -> profiles.firstOrNull { it.id == activation.profileId && it.enabled } }
        if (manualProfile != null) {
            layers += PolicyLayer(
                source = LayerSource.ManualProfile(manualProfile.id, manualProfile.name),
                rules = manualProfile.rules,
                activeUntil = manualActivation?.until,
            )
        }

        layers += profiles
            .asSequence()
            .filter { it.enabled && it.autoActivation && it.id != manualProfile?.id }
            .filter { ScheduleEvaluator.isAnyActive(it.schedules, now) }
            .sortedWith(compareByDescending<Profile> { it.priority }.thenBy { it.id })
            .map { profile ->
                PolicyLayer(
                    source = LayerSource.ScheduledProfile(profile.id, profile.name, profile.priority),
                    rules = profile.rules,
                    activeUntil = ScheduleEvaluator.activeUntil(profile.schedules, now),
                )
            }

        if (globalRules.isNotEmpty()) {
            layers += PolicyLayer(LayerSource.Global, globalRules, activeUntil = null)
        }

        return ResolvedPolicy(layers)
    }
}
