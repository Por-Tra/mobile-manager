package com.lucas.mobilemanager.domain.zones

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Détecteurs de zones (Reels, Shorts…), décrits en JSON pour être réparés sans toucher au code
 * quand une app change son interface. Voir `assets/zone_detectors.json`.
 */
@Serializable
data class ZoneDetectorFile(
    val version: Int,
    val apps: List<AppZones>,
) {
    fun zonesFor(packageName: String): List<ZoneDefinition> =
        apps.firstOrNull { it.packageName == packageName }?.zones.orEmpty()

    val packages: Set<String> get() = apps.map { it.packageName }.toSet()

    companion object {
        const val SUPPORTED_VERSION = 1
        val EMPTY = ZoneDetectorFile(SUPPORTED_VERSION, emptyList())
    }
}

@Serializable
data class AppZones(
    @SerialName("package") val packageName: String,
    val zones: List<ZoneDefinition>,
)

@Serializable
data class ZoneDefinition(
    val id: String,
    val label: String,
    /** La zone est détectée si au moins un de ces signaux correspond à un nœud… */
    val anyOf: List<NodeSignal>,
    /** …et qu'aucun de ceux-ci ne correspond (évite les faux positifs, ex. Stories). */
    val noneOf: List<NodeSignal> = emptyList(),
    /**
     * Actions de sortie : la première applicable au premier essai, la suivante si la zone est
     * toujours là, etc. Le retour arrière sert de dernier recours.
     */
    val exit: List<ExitAction> = listOf(ExitAction(back = true)),
    /** Points d'entrée à masquer quand la zone n'est pas accessible (bouton Reels, onglet Shorts). */
    val hide: List<NodeSignal> = emptyList(),
)

/**
 * Conditions portant sur **un même nœud** de l'arbre d'accessibilité. Toutes les conditions
 * renseignées doivent être vraies. Comparaisons de texte insensibles à la casse ; les listes
 * permettent les variantes de langue (« Accueil », « Home »).
 */
@Serializable
data class NodeSignal(
    /** Identifiant exact, complet (`pkg:id/nom`) ou court (`nom`). */
    val viewId: String? = null,
    val viewIdContains: String? = null,
    val text: List<String>? = null,
    val textContains: List<String>? = null,
    val desc: List<String>? = null,
    val descContains: List<String>? = null,
    val selected: Boolean? = null,
    val clickable: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = viewId == null && viewIdContains == null && text == null && textContains == null &&
            desc == null && descContains == null && selected == null && clickable == null
}

/** Une action de sortie : cliquer un nœud (onglet Accueil…) ou revenir en arrière. */
@Serializable
data class ExitAction(
    val clickViewId: String? = null,
    val clickDesc: List<String>? = null,
    val clickText: List<String>? = null,
    val back: Boolean = false,
) {
    val definedActions: Int
        get() = listOf(clickViewId != null, clickDesc != null, clickText != null, back).count { it }
}

/** Nœud de l'arbre d'accessibilité, indépendant d'Android. */
@Serializable
data class UiNode(
    val viewId: String? = null,
    val text: String? = null,
    val desc: String? = null,
    val selected: Boolean = false,
    val clickable: Boolean = false,
    val depth: Int = 0,
    val className: String? = null,
    /** `false` = présent dans l'arbre mais hors écran (onglet gardé en mémoire) : jamais pris en compte. */
    val visible: Boolean = true,
)
