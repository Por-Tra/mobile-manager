package com.lucas.mobilemanager.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * Après un redémarrage (ou une mise à jour de l'app), relance le service si une session est
 * toujours en cours. L'heure de fin étant absolue, la session reprend exactement où elle en était.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val entryPoint = SessionEntryPoint.from(context)
        val pending = goAsync()
        entryPoint.appScope().launch {
            try {
                entryPoint.strictSessionManager().completeIfDue()
                val now = entryPoint.timeProvider().now()
                if (entryPoint.strictSessionRepository().current()?.isRunning(now) == true) {
                    StrictSessionService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
