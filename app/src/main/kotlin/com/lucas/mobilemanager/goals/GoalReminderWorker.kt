package com.lucas.mobilemanager.goals

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lucas.mobilemanager.MainActivity
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.goals.GoalRepository
import com.lucas.mobilemanager.domain.mode.ModePolicy
import com.lucas.mobilemanager.domain.repository.ModeRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.TimeUnit

@HiltWorker
class GoalReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val goals: GoalRepository,
    private val modes: ModeRepository,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!ModePolicy.blocksEnabled(modes.settings.first())) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return Result.success()
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.goal_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val now = time.localNow()
        val active = goals.goals.first().filter { it.remindersEnabled && it.isActive(now.toLocalDate()) && it.slots.isNotEmpty() }
        if (active.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork("goal_preparation")
            return Result.success()
        }
        active.forEach { goal ->
            goal.slots.filter { now.dayOfWeek in it.days }.forEach { slot ->
                val start = now.toLocalDate().atTime(slot.start).atZone(now.zone)
                val late = Duration.between(start, now)
                if (!late.isNegative && late < Duration.ofMinutes(minOf(slot.minutes, 30).toLong())) {
                    val key = "${goal.id}/${slot.start}"
                    if (goals.claimReminder(key, now.toLocalDate())) {
                        val intent = PendingIntent.getActivity(context, 0,
                            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                .putExtra("open_goals", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        manager.notify(key.hashCode(), NotificationCompat.Builder(context, CHANNEL)
                            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(goal.title)
                            .setContentText(context.getString(R.string.goal_reminder_body, slot.minutes))
                            .setContentIntent(intent).setAutoCancel(true).build())
                    }
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "goal_preparation"
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("goal_preparation", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GoalReminderWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
