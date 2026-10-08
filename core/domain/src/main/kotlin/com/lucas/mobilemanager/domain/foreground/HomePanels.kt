package com.lucas.mobilemanager.domain.foreground

/**
 * Écrans qui font partie du bureau même s'ils appartiennent à une autre app : le flux Google
 * (« Discover », à gauche du bureau) sur les launchers Pixel récents est une vraie activité de
 * l'app Google, intégrée à la tâche du launcher. Ouvrir ce panneau ne doit pas compter comme
 * « ouvrir l'app Google ».
 *
 * Sur la plupart des launchers (dont One UI), ces panneaux sont de simples fenêtres et sont déjà
 * ignorés par [ForegroundTracker] car ce ne sont pas des activités. Pour en ajouter un : lire le
 * package et la classe dans `adb logcat -s MobileManager` (ligne « window »).
 */
object HomePanels {

    private data class Panel(val packageName: String, val classFragment: String)

    private val PANELS = listOf(
        // Flux Google sur Pixel Launcher (activité intégrée au bureau).
        Panel("com.google.android.googlequicksearchbox", ".launcher.minusone."),
    )

    fun isHomePanel(packageName: String, className: String?): Boolean =
        className != null && PANELS.any { it.packageName == packageName && it.classFragment in className }
}
