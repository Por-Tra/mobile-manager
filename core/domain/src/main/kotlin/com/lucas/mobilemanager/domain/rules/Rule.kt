package com.lucas.mobilemanager.domain.rules

import java.time.Duration

/** Ce qu'une règle vise, du plus général au plus précis. */
sealed interface Target {
    /** Précision : plus la valeur est grande, plus la cible est précise. */
    val specificity: Int

    fun matches(request: AppRequest): Boolean

    /** Toutes les applications (plages horaires interdites). */
    data object AllApps : Target {
        override val specificity = 0
        override fun matches(request: AppRequest) = true
    }

    data class App(val packageName: String) : Target {
        override val specificity = 1
        override fun matches(request: AppRequest) = request.packageName == packageName
    }

    /** Zone d'une application (ex. Instagram / « reels »). Ne s'applique que si la zone est détectée. */
    data class Zone(val packageName: String, val zone: String) : Target {
        override val specificity = 2
        override fun matches(request: AppRequest) =
            request.packageName == packageName && request.zone == zone
    }
}

sealed interface RuleAction {
    /** Exception explicite : stoppe les restrictions des niveaux inférieurs. */
    data object Allow : RuleAction

    /** Limite de temps quotidienne, remise à zéro à minuit local. */
    data class Limit(val dailyLimit: Duration) : RuleAction {
        init {
            require(!dailyLimit.isNegative) { "dailyLimit must not be negative" }
        }
    }

    data object Block : RuleAction
}

data class Rule(val target: Target, val action: RuleAction)
