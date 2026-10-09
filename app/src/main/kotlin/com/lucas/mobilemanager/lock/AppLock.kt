package com.lucas.mobilemanager.lock

import android.os.SystemClock
import android.util.Log
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.lock.LockConfig
import com.lucas.mobilemanager.domain.lock.LockPolicy
import com.lucas.mobilemanager.domain.lock.PinHasher
import com.lucas.mobilemanager.domain.repository.LockRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** État du code du proche, pour l'interface. */
data class LockStatus(
    val isSet: Boolean,
    /** Fin du déverrouillage en cours, `null` si verrouillé. */
    val unlockedUntil: Instant?,
    /** Fin de l'attente après trop d'essais, `null` si aucune. */
    val lockedOutUntil: Instant?,
) {
    /** Les réglages qui assouplissent les restrictions sont modifiables. */
    val canModify: Boolean get() = !isSet || unlockedUntil != null

    companion object {
        val NOT_SET = LockStatus(isSet = false, unlockedUntil = null, lockedOutUntil = null)
    }
}

/**
 * Code du proche : tant qu'il est défini, toute modification des restrictions demande de le
 * saisir. Le déverrouillage (5 min) est gardé en mémoire : un redémarrage reverrouille.
 */
@Singleton
class AppLock @Inject constructor(
    private val repository: LockRepository,
    private val timeProvider: TimeProvider,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Enveloppe pour distinguer « pas encore lu » (`null`) de « aucun code ». */
    private data class Stored(val config: LockConfig?)

    private val stored: StateFlow<Stored?> = repository.config.map(::Stored)
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    private val unlockedUntil = MutableStateFlow<Instant?>(null)

    /** Battement d'une seconde : la fin du déverrouillage ou de l'attente se voit sans action. */
    private val ticks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    val status: Flow<LockStatus> = combine(stored.filterNotNull(), unlockedUntil, ticks) { stored, until, _ ->
        val now = timeProvider.now()
        val config = stored.config ?: return@combine LockStatus.NOT_SET
        LockStatus(
            isSet = true,
            unlockedUntil = until?.takeIf { now.isBefore(it) },
            lockedOutUntil = config.lockedOutUntil?.takeIf { now.isBefore(it) },
        )
    }.distinctUntilChanged()

    /** Vérification immédiate pour les ViewModels : refuse tant que le code n'a pas été lu. */
    fun canModifyNow(): Boolean {
        if ((stored.value ?: return false).config == null) return true
        return isUnlocked()
    }

    /** Pour le service d'accessibilité : un code existe et l'app n'est pas déverrouillée. */
    fun protectsSettingsNow(): Boolean = stored.value?.config != null && !isUnlocked()

    private fun isUnlocked(): Boolean = unlockedUntil.value?.let { timeProvider.now().isBefore(it) } == true

    /** Un essai du code. En cas de succès, les réglages sont modifiables pendant 5 min. */
    suspend fun unlock(pin: String): LockPolicy.Attempt {
        val config = stored.filterNotNull().first().config ?: return LockPolicy.Attempt.Success
        val now = timeProvider.now()
        // PBKDF2 est volontairement coûteux : hors du thread principal.
        val startedMs = SystemClock.elapsedRealtime()
        val (result, updated) = withContext(Dispatchers.Default) { LockPolicy.attempt(config, pin, now) }
        if (BuildConfig.DEBUG) Log.d(TAG, "unlock attempt in ${SystemClock.elapsedRealtime() - startedMs} ms -> $result")
        if (updated != config) repository.save(updated)
        if (result == LockPolicy.Attempt.Success) unlockedUntil.value = now + LockPolicy.UNLOCK_DURATION
        return result
    }

    fun lockNow() {
        unlockedUntil.value = null
    }

    /** Définit (ou remplace) le code ; possible seulement sans code ou déverrouillé. */
    suspend fun setPin(pin: String): Boolean {
        if (!canModifyNow() || !LockPolicy.isValidPin(pin)) return false
        val config = withContext(Dispatchers.Default) { PinHasher.newConfig(pin) }
        repository.save(config)
        unlockedUntil.value = timeProvider.now() + LockPolicy.UNLOCK_DURATION
        return true
    }

    /** Retire le code ; possible seulement déverrouillé. */
    suspend fun removePin(): Boolean {
        if (!canModifyNow()) return false
        repository.save(null)
        unlockedUntil.value = null
        return true
    }

    private companion object {
        const val TAG = "MobileManager"
    }
}
