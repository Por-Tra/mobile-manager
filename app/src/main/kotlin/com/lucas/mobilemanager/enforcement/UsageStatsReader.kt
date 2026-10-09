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
import java.time.LocalDate
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
     * Temps d'écran de chaque app pendant le jour local [date] (jusqu'à maintenant pour
     * aujourd'hui), ou `null` si Android n'a plus aucun événement pour ce jour (au-delà
     * d'environ une semaine) : « pas de données », à ne pas confondre avec « aucun usage ».
     */
    fun usageByPackageOn(date: LocalDate): Map<String, Duration>? {
        val zone = timeProvider.zone()
        val start = date.atStartOfDay(zone).toInstant()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), timeProvider.now())
        if (!end.isAfter(start)) return null
        var anyEvent = false
        val events = readEvents(start.minus(LOOKBACK_BEFORE_MIDNIGHT), end, packageName = null) { anyEvent = true }
        if (!anyEvent) return null
        return UsageCalculator.usageByPackage(events, start, end)
    }

    /**
     * Événements reprise/pause, d'une app ou de toutes ([packageName] `null`). On remonte un peu
     * avant minuit pour retrouver la reprise d'une app ouverte la veille ; le calcul coupe ensuite.
     */
    private fun readEvents(
        from: Instant,
        to: Instant,
        packageName: String?,
        onAnyEvent: () -> Unit = {},
    ): List<UsageEvent> {
        val result = mutableListOf<UsageEvent>()
        val events = usageStatsManager.queryEvents(from.toEpochMilli(), to.toEpochMilli()) ?: return result
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            onAnyEvent()
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Type.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Type.PAUSED
                // Écran éteint, arrêt ou démarrage de l'appareil : clôt l'app au premier plan,
                // même si sa pause n'a pas été enregistrée (coupure brutale).
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                UsageEvents.Event.DEVICE_STARTUP,
                -> UsageEvent.Type.INTERRUPTION
                else -> continue
            }
            if (packageName != null && type != UsageEvent.Type.INTERRUPTION && event.packageName != packageName) continue
            result += UsageEvent(event.packageName, type, Instant.ofEpochMilli(event.timeStamp))
        }
        return result
    }

    private companion object {
        val LOOKBACK_BEFORE_MIDNIGHT: Duration = Duration.ofHours(6)
        val CONTINUOUS_SESSION_WINDOW: Duration = Duration.ofHours(12)
    }
}
