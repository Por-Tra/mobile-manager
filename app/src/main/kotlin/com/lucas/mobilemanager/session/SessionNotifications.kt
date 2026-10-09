package com.lucas.mobilemanager.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.lucas.mobilemanager.MainActivity
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.ui.format.Formats
import java.time.ZoneId

/** Notifications de la session de concentration (persistante) et des alertes de protection. */
object SessionNotifications {

    const val SESSION_NOTIFICATION_ID = 1
    const val PROTECTION_NOTIFICATION_ID = 2

    private const val CHANNEL_SESSION = "strict_session"
    private const val CHANNEL_PROTECTION = "protection"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION,
                context.getString(R.string.channel_session),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROTECTION,
                context.getString(R.string.channel_protection),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /** Notification affichée le temps de lire la session au démarrage du service. */
    fun starting(context: Context): Notification =
        sessionBuilder(context).setContentText(context.getString(R.string.session_notification_starting)).build()

    /** « Session de concentration · Fin à 15:30 » avec compte à rebours. */
    fun session(context: Context, session: StrictSession, zone: ZoneId): Notification {
        val endTime = Formats.TIME.format(session.endsAt.atZone(zone))
        return sessionBuilder(context)
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.session_notification_text,
                    session.blockedPackages.size,
                    endTime,
                    session.blockedPackages.size,
                ),
            )
            .setWhen(session.endsAt.toEpochMilli())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
    }

    /** Alerte si la surveillance est coupée pendant une session. */
    fun protectionDisabled(context: Context, session: StrictSession, zone: ZoneId): Notification {
        val endTime = Formats.TIME.format(session.endsAt.atZone(zone))
        val reenable = PendingIntent.getActivity(
            context,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.protection_title))
            .setContentText(context.getString(R.string.protection_body, endTime))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.protection_body, endTime)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(reenable)
            .addAction(0, context.getString(R.string.protection_reenable), reenable)
            .setAutoCancel(true)
            .build()
    }

    private fun sessionBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.session_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
}
