package com.lucas.mobilemanager.domain.foreground

/**
 * Déduit l'app réellement au premier plan à partir des changements de fenêtre.
 *
 * Seules les fenêtres d'**activité** comptent : le flux Google à gauche du bureau, les widgets,
 * le volet de notifications ou le clavier ouvrent des fenêtres appartenant à d'autres packages
 * sans que l'utilisateur ait quitté l'app (ou le bureau) en cours.
 */
class ForegroundTracker(private val selfPackage: String) {

    sealed interface Change {
        /** Rien à faire : superposition, fenêtre non-activité ou même app. */
        data object None : Change

        /** Un écran de Mobile Manager (dont l'écran de blocage) est passé au premier plan. */
        data object Self : Change

        /** Une autre app est passée au premier plan : à évaluer. */
        data class App(val packageName: String) : Change
    }

    var current: String? = null
        private set

    /**
     * @param isActivity la fenêtre correspond à une activité déclarée par [packageName].
     * @param overlayPackages packages qui se superposent sans remplacer l'app (volet, claviers).
     */
    fun onWindowStateChanged(
        packageName: String?,
        isActivity: Boolean,
        overlayPackages: Set<String>,
    ): Change {
        if (packageName == null || packageName in overlayPackages || !isActivity) return Change.None
        if (packageName == current) return Change.None
        current = packageName
        return if (packageName == selfPackage) Change.Self else Change.App(packageName)
    }

    /** Oublie l'app courante, pour qu'elle soit réévaluée à sa prochaine apparition (après un blocage). */
    fun reset() {
        current = null
    }
}
