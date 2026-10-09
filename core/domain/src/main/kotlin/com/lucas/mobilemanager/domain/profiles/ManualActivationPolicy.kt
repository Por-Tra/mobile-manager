package com.lucas.mobilemanager.domain.profiles

import com.lucas.mobilemanager.domain.time.LocalTimes
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Durée d'une activation manuelle de profil. Désactivable à tout moment, quelle que soit l'option. */
sealed interface ManualDuration {
    data class For(val duration: Duration) : ManualDuration
    data class Until(val time: LocalTime) : ManualDuration
    data object UntilTurnedOff : ManualDuration

    companion object {
        val ONE_HOUR = For(Duration.ofHours(1))
        val TWO_HOURS = For(Duration.ofHours(2))
    }
}

object ManualActivationPolicy {

    /** Fin de l'activation, `null` si elle dure jusqu'à désactivation. */
    fun until(option: ManualDuration, now: ZonedDateTime): Instant? = when (option) {
        is ManualDuration.For -> now.toInstant() + option.duration
        is ManualDuration.Until -> LocalTimes.nextOccurrence(option.time, now).toInstant()
        ManualDuration.UntilTurnedOff -> null
    }
}
