package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.model.Profile

/**
 * L'ordre affiché des profils est leur ordre de priorité : le premier l'emporte quand deux
 * profils planifiés se chevauchent (même départage que `ProfileResolver`).
 */
object ProfileOrdering {

    const val STEP = 10

    fun sorted(profiles: List<Profile>): List<Profile> =
        profiles.sortedWith(compareByDescending<Profile> { it.priority }.thenBy { it.id })

    /**
     * Déplace le profil de [from] à [to] dans [profiles] (ordre affiché) et réattribue des
     * priorités espacées et strictement décroissantes. Ne renvoie que les profils modifiés.
     */
    fun reorder(profiles: List<Profile>, from: Int, to: Int): List<Profile> {
        require(from in profiles.indices && to in profiles.indices) { "index out of range" }
        val moved = profiles.toMutableList().apply { add(to, removeAt(from)) }
        return moved.mapIndexed { index, profile -> profile.copy(priority = (moved.size - index) * STEP) }
            .filterIndexed { index, profile -> profile.priority != moved[index].priority }
    }

    /** Priorité d'un nouveau profil : en bas de la liste. */
    fun nextPriority(profiles: List<Profile>): Int =
        profiles.minOfOrNull { it.priority }?.minus(STEP) ?: (10 * STEP)
}
