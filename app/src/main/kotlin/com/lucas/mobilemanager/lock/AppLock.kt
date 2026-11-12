package com.lucas.mobilemanager.lock

import android.os.SystemClock
import android.util.Log
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.lock.LockConfig
import com.lucas.mobilemanager.domain.lock.LockPolicy
import com.lucas.mobilemanager.domain.lock.PinHasher
import com.lucas.mobilemanager.domain.mode.AppMode
import com.lucas.mobilemanager.domain.mode.ModePolicy
import com.lucas.mobilemanager.domain.mode.ModeSettings
import com.lucas.mobilemanager.domain.mode.ModeUnlockManager
import com.lucas.mobilemanager.domain.repository.LockRepository
import com.lucas.mobilemanager.domain.repository.ModeRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** État du déverrouillage des réglages, par code du proche ou attente semi-libre. */
data class LockStatus(
    val isSet: Boolean,
    /** Fin du déverrouillage en cours, `null` si verrouillé. */
    val unlockedUntil: Instant?,
    /** Fin de l'attente après trop d'essais, `null` si aucune. */
    val lockedOutUntil: Instant?,
    val requiresWaiting: Boolean = false,
    val waitReadyAt: Instant? = null,
    val waitRemainingSeconds: Long? = null,
) {
    /** Les réglages qui assouplissent les restrictions sont modifiables. */
    val canModify: Boolean get() = unlockedUntil != null || !isSet && !requiresWaiting

    companion object {
        val NOT_SET = LockStatus(isSet = false, unlockedUntil = null, lockedOutUntil = null)
    }
}

/**
 * Autorise les modifications des restrictions selon le mode choisi, le code du proche et
 * l'attente semi-libre. Le déverrouillage de 5 min est en mémoire : un redémarrage reverrouille.
 */
@Singleton
class AppLock @Inject constructor(
    private val repository: LockRepository,
    private val modeRepository: ModeRepository,
    private val sessions: StrictSessionRepository,
    private val modeUnlockManager: ModeUnlockManager,
    private val timeProvider: TimeProvider,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Enveloppe pour distinguer « pas encore lu » (`null`) de « aucun code ». */
    private data class Stored(val config: LockConfig?)

    private val stored: StateFlow<Stored?> = repository.config.map(::Stored)
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    private val unlockedUntil = MutableStateFlow<Instant?>(null)
    private val changes = Mutex()

    val modeSettings: StateFlow<ModeSettings?> = modeRepository.settings
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /** Battement d'une seconde : la fin du déverrouillage ou de l'attente se voit sans action. */
    private val ticks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    val status: Flow<LockStatus> = combine(stored.filterNotNull(), modeSettings.filterNotNull(), unlockedUntil, ticks) { stored, mode, until, _ ->
        val now = timeProvider.now()
        val config = stored.config
        LockStatus(
            isSet = config != null,
            unlockedUntil = until?.takeIf { now.isBefore(it) },
            lockedOutUntil = config?.lockedOutUntil?.takeIf { now.isBefore(it) },
            requiresWaiting = ModePolicy.requiresWait(mode, hasPin = config != null),
            waitReadyAt = ModePolicy.readyAt(mode),
            waitRemainingSeconds = ModePolicy.readyAt(mode)?.let { java.time.Duration.between(now, it).seconds.coerceAtLeast(0) },
        )
    }.distinctUntilChanged()

    /** Vérification immédiate pour les ViewModels : refuse tant que le code n'a pas été lu. */
    fun canModifyNow(): Boolean {
        val config = (stored.value ?: return false).config
        val mode = modeSettings.value ?: return false
        return ModePolicy.canModify(mode, config != null, unlockedUntil.value, timeProvider.now())
    }

    /** Protège les écrans système pendant un verrouillage par code ou attente, hors mode libre. */
    fun protectsSettingsNow(): Boolean = modeSettings.value?.let(ModePolicy::blocksEnabled) == true &&
        !canModifyNow()

    suspend fun selectMode(mode: AppMode, minimumWaitMinutes: Int): Boolean = changes.withLock {
        if (!canModifyNow() || sessions.current()?.isRunning(timeProvider.now()) == true) return@withLock false
        if (minimumWaitMinutes !in 1..1440) return@withLock false
        modeRepository.update {
            ModeSettings(selectedMode = mode, minimumWaitMinutes = minimumWaitMinutes)
        }
        unlockedUntil.value = null
        true
    }

    suspend fun requestModeUnlock() = changes.withLock {
        modeUnlockManager.request()
    }

    suspend fun cancelModeUnlock() = changes.withLock {
        modeUnlockManager.cancel()
    }

    suspend fun unlockModeAfterWait(): Boolean = changes.withLock {
        val accepted = modeUnlockManager.consume()
        if (accepted) unlockedUntil.value = timeProvider.now() + LockPolicy.UNLOCK_DURATION
        accepted
    }

    /** Un essai du code. En cas de succès, les réglages sont modifiables pendant 5 min. */
    suspend fun unlock(pin: String): LockPolicy.Attempt = changes.withLock {
        val config = repository.config.first() ?: return@withLock LockPolicy.Attempt.Success
        val now = timeProvider.now()
        // PBKDF2 est volontairement coûteux : hors du thread principal.
        val startedMs = SystemClock.elapsedRealtime()
        val (result, updated) = withContext(Dispatchers.Default) { LockPolicy.attempt(config, pin, now) }
        if (BuildConfig.DEBUG) Log.d(TAG, "unlock attempt in ${SystemClock.elapsedRealtime() - startedMs} ms -> $result")
        if (updated != config) repository.save(updated)
        if (result == LockPolicy.Attempt.Success) unlockedUntil.value = now + LockPolicy.UNLOCK_DURATION
        result
    }

    fun lockNow() {
        unlockedUntil.value = null
    }

    /** Définit (ou remplace) le code ; possible seulement sans code ou déverrouillé. */
    suspend fun setPin(pin: String): Boolean = changes.withLock {
        if (!canModifyNow() || !LockPolicy.isValidPin(pin)) return@withLock false
        val config = withContext(Dispatchers.Default) { PinHasher.newConfig(pin) }
        repository.save(config)
        modeUnlockManager.cancel()
        unlockedUntil.value = timeProvider.now() + LockPolicy.UNLOCK_DURATION
        true
    }

    /** Retire le code ; possible seulement déverrouillé. */
    suspend fun removePin(): Boolean = changes.withLock {
        if (!canModifyNow()) return@withLock false
        repository.save(null)
        modeUnlockManager.cancel()
        unlockedUntil.value = null
        true
    }

    private companion object {
        const val TAG = "MobileManager"
    }
}
