package com.lucas.mobilemanager.domain.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtectedScreenDetectorTest {

    private val labels = setOf("Mobile Manager", "Surveillance Mobile Manager")
    private val settings = "com.android.settings"

    private fun close(pkg: String, vararg texts: String, running: Boolean = true) =
        ProtectedScreenDetector.shouldClose(pkg, texts.asSequence(), labels, running)

    @Test
    fun `app info page is closed`() =
        assertTrue(close(settings, "Infos sur l'appli", "Mobile Manager", "Désinstaller", "Forcer l'arrêt"))

    @Test
    fun `accessibility service page is closed`() =
        assertTrue(close(settings, "Surveillance Mobile Manager", "Utiliser Surveillance Mobile Manager"))

    @Test
    fun `apps list showing mobile manager is closed`() =
        assertTrue(close(settings, "Applis", "Chrome", "Mobile Manager", "YouTube"))

    @Test
    fun `uninstall dialog is closed`() =
        assertTrue(close("com.google.android.packageinstaller", "Voulez-vous désinstaller Mobile Manager ?"))

    @Test
    fun `samsung settings are covered`() =
        assertTrue(close("com.samsung.android.settings", "Mobile Manager"))

    @Test
    fun `matching ignores case`() = assertTrue(close(settings, "MOBILE MANAGER"))

    @Test
    fun `settings screen without mobile manager stays open`() =
        assertFalse(close(settings, "Wi-Fi", "Bluetooth", "Affichage"))

    @Test
    fun `nothing is closed outside a session`() =
        assertFalse(close(settings, "Mobile Manager", running = false))

    @Test
    fun `other apps showing the name are not closed`() =
        assertFalse(close("com.google.android.apps.nexuslauncher", "Mobile Manager"))
}
