package com.lucas.mobilemanager.domain.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Code du proche enregistré : jamais en clair, seulement son empreinte PBKDF2 et le sel.
 * [failures] et [lockedOutUntil] limitent les essais (ils survivent à un redémarrage).
 */
data class LockConfig(
    val pinHash: String,
    val salt: String,
    /** Itérations PBKDF2 utilisées pour cette empreinte (le réglage peut évoluer). */
    val iterations: Int = PinHasher.ITERATIONS,
    val failures: Int = 0,
    val lockedOutUntil: Instant? = null,
)

/** Empreinte du code : PBKDF2-HMAC-SHA256, sel aléatoire, comparaison en temps constant. */
object PinHasher {
    // Mesuré sur un Galaxy A04s : 120 000 itérations = 2,7 s. La vraie protection est la limite
    // d'essais (un code de 4 à 8 chiffres reste devinable hors ligne, quelle que soit l'empreinte).
    const val ITERATIONS = 30_000

    /** Premières empreintes, enregistrées sans leur nombre d'itérations. */
    const val LEGACY_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun newConfig(pin: String, random: SecureRandom = SecureRandom()): LockConfig {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return LockConfig(pinHash = encode(hash(pin, salt, ITERATIONS)), salt = encode(salt), iterations = ITERATIONS)
    }

    fun matches(pin: String, config: LockConfig): Boolean {
        val expected = decode(config.pinHash) ?: return false
        val salt = decode(config.salt) ?: return false
        return MessageDigest.isEqual(hash(pin, salt, config.iterations), expected)
    }

    private fun hash(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String) = try {
        Base64.getDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** Règles du code du proche : format, durée de déverrouillage, essais limités. */
object LockPolicy {
    val PIN_LENGTH = 4..8

    /** Après le bon code, les réglages restent modifiables pendant ce temps. */
    val UNLOCK_DURATION: Duration = Duration.ofMinutes(5)

    /** Essais libres avant la première attente. */
    const val FREE_ATTEMPTS = 5
    private val FIRST_LOCKOUT: Duration = Duration.ofSeconds(30)
    private val MAX_LOCKOUT: Duration = Duration.ofHours(1)

    fun isValidPin(pin: String): Boolean = pin.length in PIN_LENGTH && pin.all { it in '0'..'9' }

    /** Attente imposée après [failures] échecs consécutifs : 30 s au 5e, puis double à chaque échec, 1 h au plus. */
    fun lockoutFor(failures: Int): Duration? {
        if (failures < FREE_ATTEMPTS) return null
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
        return FIRST_LOCKOUT.multipliedBy(1L shl doublings).coerceAtMost(MAX_LOCKOUT)
    }

    sealed interface Attempt {
        data object Success : Attempt

        /** Mauvais code ; [attemptsLeft] avant une attente. */
        data class Wrong(val attemptsLeft: Int) : Attempt

        /** Trop d'essais : nouvel essai possible à [until]. */
        data class LockedOut(val until: Instant) : Attempt
    }

    /**
     * Un essai de [pin] à [now]. Renvoie le résultat et la configuration à enregistrer
     * (compteur d'échecs, attente). Pendant une attente, le code n'est même pas vérifié.
     */
    fun attempt(config: LockConfig, pin: String, now: Instant, matches: (String, LockConfig) -> Boolean = PinHasher::matches): Pair<Attempt, LockConfig> {
        config.lockedOutUntil?.let { until -> if (now.isBefore(until)) return Attempt.LockedOut(until) to config }
        if (matches(pin, config)) return Attempt.Success to config.copy(failures = 0, lockedOutUntil = null)
        val failures = config.failures + 1
        val lockout = lockoutFor(failures)?.let { now + it }
        val updated = config.copy(failures = failures, lockedOutUntil = lockout)
        return (if (lockout != null) Attempt.LockedOut(lockout) else Attempt.Wrong(FREE_ATTEMPTS - failures)) to updated
    }

    /** Au-delà, l'attente est de toute façon plafonnée à une heure (évite un débordement). */
    private const val MAX_DOUBLINGS = 16
}
