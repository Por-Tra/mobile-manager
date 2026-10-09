package com.lucas.mobilemanager.enforcement

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.lucas.mobilemanager.domain.time.DayBoundaries
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.domain.usage.ContinuousSession
import com.lucas.mobilemanager.domain.usage.ContinuousSessions
import com.lucas.mobilemanager.domain.usage.UsageCalculator
import com.lucas.mobilemanager.domain.usage.UsageEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Lit les événements de premier plan du système et en déduit le temps passé aujourd'hui. */
@Singleton
class UsageStatsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    /**
     * Session continue en cours sur [packageName] (`null` si l'app n'est pas au premier plan),
     * calculée sur les événements des 12 dernières heures.
     */
    fun continuousSession(packageName: String, gap: Duration): ContinuousSession? {
        val now = timeProvider.now()
        val from = now.minus(CONTINUOUS_SESSION_WINDOW)
        return ContinuousSessions.current(readEvents(from, now, packageName), packageName, from, now, gap)
    }

    /** Temps passé dans [packageName] depuis minuit local. */
    fun usageToday(packageName: String): Duration {
        val now = timeProvider.now()
        val midnight = DayBoundaries.startOfDay(now, timeProvider.zone())
        val events = readEvents(midnight.minus(LOOKBACK_BEFORE_MIDNIGHT), now, packageName)
        return UsageCalculator.usageSince(events, packageName, midnight, now)
    }

    /**
     * Événements reprise/pause. On remonte un peu avant [from] pour retrouver la reprise d'une
     * app ouverte avant minuit ; le calcul coupe ensuite à minuit.
     */
    private fun readEvents(from: Instant, to: Instant, packageName: String): List<UsageEvent> {
        val result = mutableListOf<UsageEvent>()
        val events = usageStatsManager.queryEvents(from.toEpochMilli(), to.toEpochMilli()) ?: return result
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Type.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Type.PAUSED
                else -> continue
            }
            result += UsageEvent(event.packageName, type, Instant.ofEpochMilli(event.timeStamp))
        }
        return result
    }

    private companion object {
        val LOOKBACK_BEFORE_MIDNIGHT: Duration = Duration.ofHours(6)
        val CONTINUOUS_SESSION_WINDOW: Duration = Duration.ofHours(12)
    }
}
