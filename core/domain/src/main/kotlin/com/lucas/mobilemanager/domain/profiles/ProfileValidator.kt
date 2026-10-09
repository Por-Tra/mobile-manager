package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import java.time.Duration

/** Vérifie qu'un profil (ou les règles par défaut) est cohérent avant enregistrement. */
object ProfileValidator {

    val MIN_LIMIT: Duration = Duration.ofMinutes(1)
    val MAX_LIMIT: Duration = Duration.ofMinutes(600)

    enum class Error {
        NAME_EMPTY,
        NAME_TAKEN,
        NO_SCHEDULE,
        SCHEDULE_WITHOUT_DAYS,
        NO_RULES,
        LIMIT_OUT_OF_RANGE,
        DUPLICATE_TARGET,
    }

    /** @param otherNames noms des autres profils (le profil édité exclu). */
    fun validate(profile: Profile, otherNames: Collection<String>): Set<Error> = buildSet {
        val name = profile.name.trim()
        if (name.isEmpty()) add(Error.NAME_EMPTY)
        if (otherNames.any { it.trim().equals(name, ignoreCase = true) }) add(Error.NAME_TAKEN)
        if (profile.autoActivation && profile.schedules.isEmpty()) add(Error.NO_SCHEDULE)
        if (profile.schedules.any { it.days.isEmpty() }) add(Error.SCHEDULE_WITHOUT_DAYS)
        if (profile.rules.isEmpty()) add(Error.NO_RULES)
        addAll(validateRules(profile.rules))
    }

    /** Règles par défaut : la liste peut être vide (aucune règle globale). */
    fun validateRules(rules: List<Rule>): Set<Error> = buildSet {
        val limits = rules.mapNotNull { it.action as? RuleAction.Limit }
        if (limits.any { it.dailyLimit < MIN_LIMIT || it.dailyLimit > MAX_LIMIT }) add(Error.LIMIT_OUT_OF_RANGE)
        if (rules.map { it.target }.toSet().size != rules.size) add(Error.DUPLICATE_TARGET)
    }
}
