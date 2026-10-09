package com.lucas.mobilemanager.domain.zones

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Lecture et validation d'un fichier de détecteurs, avec des erreurs lisibles. */
object ZoneDetectorParser {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    sealed interface Result {
        data class Valid(val file: ZoneDetectorFile) : Result
        data class Invalid(val message: String) : Result
    }

    fun parse(text: String): Result {
        val file = try {
            json.decodeFromString(ZoneDetectorFile.serializer(), text)
        } catch (e: SerializationException) {
            return Result.Invalid("JSON invalide : ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
        } catch (e: IllegalArgumentException) {
            return Result.Invalid("JSON invalide : ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
        }
        validate(file)?.let { return Result.Invalid(it) }
        return Result.Valid(file)
    }

    fun encode(file: ZoneDetectorFile): String = json.encodeToString(ZoneDetectorFile.serializer(), file)

    private fun validate(file: ZoneDetectorFile): String? {
        if (file.version != ZoneDetectorFile.SUPPORTED_VERSION) {
            return "Version ${file.version} non prise en charge (attendue : ${ZoneDetectorFile.SUPPORTED_VERSION})."
        }
        for (app in file.apps) {
            if (app.packageName.isBlank()) return "Une app n'a pas de package."
            for (zone in app.zones) {
                val where = "${app.packageName} / ${zone.id.ifBlank { "?" }}"
                if (zone.id.isBlank()) return "Une zone de ${app.packageName} n'a pas d'id."
                if (zone.anyOf.isEmpty()) return "$where : « anyOf » doit contenir au moins un signal."
                if ((zone.anyOf + zone.noneOf).any { it.isEmpty }) return "$where : un signal ne contient aucune condition."
                if (zone.exit.any { it.definedActions != 1 }) return "$where : chaque action de sortie doit définir une seule action."
            }
        }
        return null
    }
}

/** Zone détectée, avec ce qui l'a déclenchée (affiché par le mode diagnostic). */
data class ZoneMatch(
    val zone: ZoneDefinition,
    /** Indice du signal `anyOf` qui a correspondu. */
    val signalIndex: Int,
    /** Indices des nœuds qui ont déclenché ce signal. */
    val nodeIndices: List<Int>,
)

object ZoneMatcher {

    fun detect(file: ZoneDetectorFile, packageName: String, nodes: List<UiNode>): ZoneMatch? {
        for (zone in file.zonesFor(packageName)) {
            if (zone.noneOf.any { signal -> nodes.any { signal.matches(it) } }) continue
            zone.anyOf.forEachIndexed { index, signal ->
                val hits = nodes.indices.filter { signal.matches(nodes[it]) }
                if (hits.isNotEmpty()) return ZoneMatch(zone, index, hits)
            }
        }
        return null
    }
}

fun NodeSignal.matches(node: UiNode): Boolean {
    viewId?.let { if (!node.viewId.isViewId(it)) return false }
    viewIdContains?.let { if (node.viewId?.contains(it, ignoreCase = true) != true) return false }
    text?.let { if (!node.text.equalsAny(it)) return false }
    textContains?.let { if (!node.text.containsAny(it)) return false }
    desc?.let { if (!node.desc.equalsAny(it)) return false }
    descContains?.let { if (!node.desc.containsAny(it)) return false }
    selected?.let { if (node.selected != it) return false }
    return !isEmpty
}

/** `reel_recycler` correspond à `com.google.android.youtube:id/reel_recycler`. */
internal fun String?.isViewId(expected: String): Boolean =
    this != null && (equals(expected, ignoreCase = true) || endsWith(":id/$expected", ignoreCase = true))

private fun String?.equalsAny(options: List<String>) = this != null && options.any { trim().equals(it, ignoreCase = true) }

private fun String?.containsAny(options: List<String>) = this != null && options.any { contains(it, ignoreCase = true) }

/** Action concrète à exécuter pour sortir d'une zone. */
sealed interface ExitStep {
    /** Cliquer le nœud [nodeIndex] (ou son premier parent cliquable). */
    data class Click(val nodeIndex: Int) : ExitStep
    data object Back : ExitStep
}

object ZoneExitPlanner {

    /** Première action applicable dans l'ordre déclaré ; à défaut, retour arrière. */
    fun plan(zone: ZoneDefinition, nodes: List<UiNode>): ExitStep {
        for (action in zone.exit) {
            if (action.back) return ExitStep.Back
            val index = nodes.indexOfFirst { node ->
                (action.clickViewId != null && node.viewId.isViewId(action.clickViewId)) ||
                    (action.clickDesc != null && node.desc.equalsAny(action.clickDesc)) ||
                    (action.clickText != null && node.text.equalsAny(action.clickText))
            }
            if (index >= 0) return ExitStep.Click(index)
        }
        return ExitStep.Back
    }
}
