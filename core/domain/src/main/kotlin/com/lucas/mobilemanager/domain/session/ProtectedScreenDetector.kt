package com.lucas.mobilemanager.domain.session

/**
 * Pendant une session, referme les écrans qui permettraient de couper Mobile Manager :
 * tout écran des Paramètres ou de l'installateur de paquets qui affiche le nom de l'app
 * (infos de l'app, page du service d'accessibilité, admin de l'appareil, désinstallation…).
 *
 * Effet de bord assumé : une liste qui affiche Mobile Manager (applis, accessibilité) est
 * aussi refermée tant que l'entrée est visible.
 */
object ProtectedScreenDetector {

    /** Paramètres et installateurs de paquets (AOSP, Google, Samsung). */
    val PROTECTED_PACKAGES: Set<String> = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    /**
     * @param visibleTexts textes et descriptions des éléments à l'écran.
     * @param protectedLabels noms à repérer (nom de l'app, nom du service d'accessibilité).
     */
    fun shouldClose(
        packageName: String,
        visibleTexts: Sequence<String>,
        protectedLabels: Set<String>,
        sessionRunning: Boolean,
    ): Boolean {
        if (!sessionRunning || packageName !in PROTECTED_PACKAGES) return false
        return visibleTexts.any { text -> protectedLabels.any { text.contains(it, ignoreCase = true) } }
    }
}
