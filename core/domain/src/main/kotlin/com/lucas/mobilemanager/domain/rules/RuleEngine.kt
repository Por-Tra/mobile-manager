package com.lucas.mobilemanager.domain.rules

import com.lucas.mobilemanager.domain.time.DayBoundaries
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

/** Ce qu'on demande au moteur : « cette app, dans cette zone, avec cet usage aujourd'hui ». */
data class AppRequest(
    val packageName: String,
    /** Zone détectée dans l'app, `null` si inconnue (seules les règles App/AllApps s'appliquent). */
    val zone: String? = null,
    /** Temps passé aujourd'hui dans l'app, depuis minuit local. */
    val usageToday: Duration = Duration.ZERO,
    /** Temps passé aujourd'hui dans [zone] : c'est lui que mesure un quota posé sur la zone. */
    val zoneUsageToday: Duration = Duration.ZERO,
)

/** Apps jamais bloquées (horloge, téléphone, urgences, paramètres, Mobile Manager). */
data class SystemWhitelist(val packages: Set<String>) {
    operator fun contains(packageName: String) = packageName in packages
}

sealed interface Decision {
    /** [remainingToday] : temps restant avant la limite quotidienne (ou le quota), `null` si illimité. */
    data class Allowed(val remainingToday: Duration?) : Decision

    /** [until] : fin prévue du blocage si elle est connue. */
    data class Blocked(val reason: BlockReason, val until: Instant?) : Decision
}

/** Motif d'un blocage, de quoi rédiger un message neutre. */
sealed interface BlockReason {
    data object StrictSession : BlockReason
    data class Profile(val profileId: Long, val name: String) : BlockReason
    data object GlobalRule : BlockReason
    data class DailyLimitReached(val limit: Duration) : BlockReason
}

/**
 * Décide si une app est autorisée maintenant.
 *
 * 1. La liste blanche système passe avant tout.
 * 2. Dans chaque couche, seule la règle la plus précise compte (Zone > App > AllApps) ;
 *    à précision égale, la plus restrictive.
 * 3. Les couches sont parcourues de la plus prioritaire à la moins prioritaire : une règle
 *    Block/Limit est retenue, une règle Allow arrête le parcours (exception explicite).
 * 4. Parmi les restrictions retenues, la plus forte l'emporte : Block > Limit, puis la limite
 *    la plus proche d'être atteinte. Une limite posée sur une zone (quota de Reels) se mesure au
 *    temps passé dans la zone, une limite d'app au temps passé dans l'app.
 */
class RuleEngine @Inject constructor() {

    fun evaluate(
        policy: ResolvedPolicy,
        request: AppRequest,
        whitelist: SystemWhitelist,
        now: ZonedDateTime,
    ): Decision {
        if (request.packageName in whitelist) return Decision.Allowed(remainingToday = null)

        val restrictions = mutableListOf<Pair<PolicyLayer, Rule>>()
        for (layer in policy.layers) {
            val rule = layer.decisiveRule(request) ?: continue
            if (rule.action == RuleAction.Allow) break
            restrictions += layer to rule
        }

        val blockingLayers = restrictions.filter { it.second.action == RuleAction.Block }.map { it.first }
        if (blockingLayers.isNotEmpty()) {
            return Decision.Blocked(
                reason = blockingLayers.first().source.toBlockReason(),
                until = latestEnd(blockingLayers),
            )
        }

        val (limit, remaining) = restrictions
            .mapNotNull { (_, rule) ->
                val limit = rule.action as? RuleAction.Limit ?: return@mapNotNull null
                val used = if (rule.target is Target.Zone) request.zoneUsageToday else request.usageToday
                limit to limit.dailyLimit - used
            }
            .minWithOrNull(compareBy<Pair<RuleAction.Limit, Duration>> { it.second }.thenBy { it.first.dailyLimit })
            ?: return Decision.Allowed(remainingToday = null)

        return if (remaining.isNegative || remaining.isZero) {
            Decision.Blocked(
                reason = BlockReason.DailyLimitReached(limit.dailyLimit),
                until = DayBoundaries.nextMidnight(now.toInstant(), now.zone),
            )
        } else {
            Decision.Allowed(remainingToday = remaining)
        }
    }

    /** Règle la plus précise de la couche pour cette requête, la plus restrictive en cas d'égalité. */
    private fun PolicyLayer.decisiveRule(request: AppRequest): Rule? {
        val matching = rules.filter { it.target.matches(request) }
        val maxSpecificity = matching.maxOfOrNull { it.target.specificity } ?: return null
        return matching
            .filter { it.target.specificity == maxSpecificity }
            .maxWith(compareBy(STRICTNESS) { it.action })
    }

    /**
     * Le blocage dure jusqu'à la fin de la dernière couche bloquante. Si l'une d'elles n'a pas
     * de fin connue, la fin globale est inconnue.
     */
    private fun latestEnd(layers: List<PolicyLayer>): Instant? {
        val ends = layers.map { it.activeUntil ?: return null }
        return ends.max()
    }

    private fun LayerSource.toBlockReason(): BlockReason = when (this) {
        LayerSource.StrictSession -> BlockReason.StrictSession
        is LayerSource.ManualProfile -> BlockReason.Profile(profileId, name)
        is LayerSource.ScheduledProfile -> BlockReason.Profile(profileId, name)
        LayerSource.Global -> BlockReason.GlobalRule
    }

    private companion object {
        /** Allow < Limit (plus longue < plus courte) < Block. */
        val STRICTNESS: Comparator<RuleAction> = compareBy<RuleAction> {
            when (it) {
                RuleAction.Allow -> 0
                is RuleAction.Limit -> 1
                RuleAction.Block -> 2
            }
        }.thenByDescending { (it as? RuleAction.Limit)?.dailyLimit ?: Duration.ZERO }
    }
}
