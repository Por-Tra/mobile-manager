package com.lucas.mobilemanager.domain.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Source unique de l'heure pour toute la logique métier.
 * Injectée pour pouvoir tester minuit, les changements d'heure et les fuseaux.
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId

    fun localNow(): ZonedDateTime = now().atZone(zone())
}

class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
