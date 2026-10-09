package com.lucas.mobilemanager.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.session.SessionEntryPoint
import com.lucas.mobilemanager.ui.format.Formats
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Administration de l'appareil : ajoute une étape avant la désinstallation.
 * Ne rend pas la désinstallation impossible.
 */
class MobileManagerAdminReceiver : DeviceAdminReceiver() {

    /** Message affiché par Android dans la confirmation de désactivation, pendant une session. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val entryPoint = SessionEntryPoint.from(context)
        val session = runningSession(context) ?: return null
        val endTime = Formats.TIME.format(session.endsAt.atZone(entryPoint.timeProvider().zone()))
        return context.getString(R.string.admin_disable_warning, endTime)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        if (runningSession(context) == null) return
        val entryPoint = SessionEntryPoint.from(context)
        entryPoint.appScope().launch { entryPoint.strictSessionManager().recordProtectionDisabled("device_admin") }
    }

    /** Lecture courte et ponctuelle : le système attend une réponse synchrone. */
    private fun runningSession(context: Context): StrictSession? {
        val entryPoint = SessionEntryPoint.from(context)
        val now = entryPoint.timeProvider().now()
        return runBlocking { entryPoint.strictSessionRepository().current() }?.takeIf { it.isRunning(now) }
    }

    companion object {
        fun component(context: Context) = ComponentName(context, MobileManagerAdminReceiver::class.java)
    }
}
