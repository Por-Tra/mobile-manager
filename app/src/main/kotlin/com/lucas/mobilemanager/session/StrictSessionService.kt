package com.lucas.mobilemanager.session

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.util.Log
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.onboarding.PermissionKind
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.session.StrictSessionManager
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.permissions.PermissionChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

/**
 * Service de premier plan qui vit le temps d'une session de concentration :
 * notification avec l'heure de fin, clôture automatique, alerte si la surveillance est coupée.
 */
@AndroidEntryPoint
class StrictSessionService : Service() {

    @Inject lateinit var repository: StrictSessionRepository
    @Inject lateinit var manager: StrictSessionManager
    @Inject lateinit var permissionChecker: PermissionChecker
    @Inject lateinit var timeProvider: TimeProvider

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /**
     * Dernière notification de session affichée. Réutilisée si le service est relancé alors qu'il
     * tourne déjà (l'accueil le relance par sécurité) : sinon le texte provisoire l'écraserait.
     */
    @Volatile private var sessionNotification: Notification? = null

    /** Session pour laquelle l'alerte « surveillance coupée » a déjà été émise. */
    private var alertedSessionId: Long? = null

    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SessionNotifications.ensureChannels(this)
        ServiceCompat.startForeground(
            this,
            SessionNotifications.SESSION_NOTIFICATION_ID,
            sessionNotification ?: SessionNotifications.starting(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (job == null) {
            job = scope.launch {
                try {
                    followSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Suivi de session interrompu", e)
                    throw e
                }
            }
        }
        return START_STICKY
    }

    private suspend fun followSession() {
        repository.observeCurrent().collectLatest { session ->
            if (BuildConfig.DEBUG) Log.d(TAG, "session=$session")
            if (session == null || manager.completeIfDue()) {
                // La session est close : la base émettra `null` si ce n'était pas déjà le cas.
                if (session == null) stop()
                return@collectLatest
            }
            val notification = SessionNotifications.session(this, session, timeProvider.zone())
            sessionNotification = notification
            notifications.notify(SessionNotifications.SESSION_NOTIFICATION_ID, notification)
            while (true) {
                checkProtection(session)
                val untilEnd = Duration.between(timeProvider.now(), session.endsAt)
                delay(untilEnd.coerceIn(Duration.ofSeconds(1), CHECK_INTERVAL).toMillis())
                if (manager.completeIfDue()) break
            }
        }
    }

    /** Alerte une fois par coupure si le service d'accessibilité est désactivé pendant la session. */
    private suspend fun checkProtection(session: StrictSession) {
        val monitoring = permissionChecker.status(PermissionKind.ACCESSIBILITY) == PermissionStatus.GRANTED
        if (monitoring) {
            alertedSessionId = null
            notifications.cancel(SessionNotifications.PROTECTION_NOTIFICATION_ID)
            return
        }
        if (alertedSessionId == session.id) return
        alertedSessionId = session.id
        manager.recordProtectionDisabled("accessibility")
        notifications.notify(
            SessionNotifications.PROTECTION_NOTIFICATION_ID,
            SessionNotifications.protectionDisabled(this, session, timeProvider.zone()),
        )
        // Avec la superposition, Android autorise l'ouverture d'un écran depuis l'arrière-plan.
        if (Settings.canDrawOverlays(this)) {
            startActivity(
                ProtectionAlertActivity.intent(this, session.endsAt).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notifications.cancel(SessionNotifications.PROTECTION_NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MobileManager"
        private val CHECK_INTERVAL: Duration = Duration.ofSeconds(30)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, StrictSessionService::class.java))
        }
    }
}
