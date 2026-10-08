package com.lucas.mobilemanager.domain.foreground

import com.lucas.mobilemanager.domain.INSTAGRAM
import com.lucas.mobilemanager.domain.YOUTUBE
import com.lucas.mobilemanager.domain.foreground.ForegroundTracker.Change
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForegroundTrackerTest {

    private val self = "com.lucas.mobilemanager"
    private val launcher = "com.google.android.apps.nexuslauncher"
    private val googleApp = "com.google.android.googlequicksearchbox"
    private val overlays = setOf("com.android.systemui", "com.google.android.inputmethod.latin")
    private val tracker = ForegroundTracker(self)

    private fun activity(pkg: String) = tracker.onWindowStateChanged(pkg, isActivity = true, overlays)
    private fun panel(pkg: String) = tracker.onWindowStateChanged(pkg, isActivity = false, overlays)

    @Test
    fun `new app activity is reported`() = assertEquals(Change.App(INSTAGRAM), activity(INSTAGRAM))

    @Test
    fun `same app twice is reported once`() {
        activity(INSTAGRAM)
        assertEquals(Change.None, activity(INSTAGRAM))
    }

    @Test
    fun `google feed panel on the home screen is ignored`() {
        activity(launcher)
        // Le flux Discover appartient à l'app Google mais n'est pas une activité : on reste sur le bureau.
        assertEquals(Change.None, panel(googleApp))
        assertEquals(launcher, tracker.current)
    }

    @Test
    fun `repeated panel events never trigger an evaluation`() {
        activity(launcher)
        repeat(5) { assertEquals(Change.None, panel(googleApp)) }
    }

    @Test
    fun `opening the real google app is still reported`() {
        activity(launcher)
        panel(googleApp)
        assertEquals(Change.App(googleApp), activity(googleApp))
    }

    @Test
    fun `notification shade and keyboard do not replace the current app`() {
        activity(INSTAGRAM)
        assertEquals(Change.None, activity("com.android.systemui"))
        assertEquals(Change.None, activity("com.google.android.inputmethod.latin"))
        assertEquals(INSTAGRAM, tracker.current)
    }

    @Test
    fun `own screens are reported as self`() {
        activity(INSTAGRAM)
        assertEquals(Change.Self, activity(self))
        assertEquals(Change.App(INSTAGRAM), activity(INSTAGRAM))
    }

    @Test
    fun `reset makes the same app reported again`() {
        activity(YOUTUBE)
        tracker.reset()
        assertEquals(Change.App(YOUTUBE), activity(YOUTUBE))
    }

    @Test
    fun `missing package is ignored`() =
        assertEquals(Change.None, tracker.onWindowStateChanged(null, isActivity = true, overlays))
}
