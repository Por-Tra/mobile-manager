package com.lucas.mobilemanager.domain.rules

import java.time.Duration
import java.time.LocalDate

/** Une autorisation de continuer vaut uniquement pour le passage courant dans l'app. */
class OpeningPauseState {
    private var foreground: String? = null
    private var passed: String? = null

    /** Renvoie vrai quand il faut retirer une éventuelle pause de l'ancien premier plan. */
    fun onForeground(packageName: String?): Boolean {
        if (foreground == packageName) return false
        foreground = packageName
        passed = null
        return true
    }

    fun needsPause(packageName: String): Boolean = foreground == packageName && passed != packageName
    fun continueOpening(packageName: String) { if (foreground == packageName) passed = packageName }
    fun reset() { foreground = null; passed = null }
}

/** Préavis par jour local ; un usage sans limite ou déjà bloqué ne produit aucun préavis. */
class LimitWarningPolicy {
    private var day: LocalDate? = null
    private val warned = mutableSetOf<String>()

    fun shouldWarn(date: LocalDate, key: String, remaining: Duration?, enabled: Boolean): Boolean {
        if (date != day) { day = date; warned.clear() }
        if (!enabled || remaining == null || remaining <= Duration.ZERO || remaining > Duration.ofMinutes(5)) return false
        return warned.add(key)
    }
}
