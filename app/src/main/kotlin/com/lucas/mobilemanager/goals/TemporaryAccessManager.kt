package com.lucas.mobilemanager.goals

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.goals.GoalRepository
import com.lucas.mobilemanager.domain.goals.TemporaryAccess
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.lock.AppLock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemporaryAccessManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: GoalRepository,
    private val sessions: StrictSessionRepository,
    private val lock: AppLock,
    private val time: TimeProvider,
    @ApplicationScope scope: CoroutineScope,
) {
    val access = repository.temporaryAccess.stateIn(scope, SharingStarted.Eagerly, null)
    private val mutex = Mutex()
    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    suspend fun remaining(packageName: String): Duration? = access.filterNotNull().first()
        .firstOrNull { it.packageName == packageName }
        ?.remaining(time.now(), bootCount(), SystemClock.elapsedRealtime())

    fun remainingNow(packageName: String): Duration? = access.value?.firstOrNull { it.packageName == packageName }
        ?.remaining(time.now(), bootCount(), SystemClock.elapsedRealtime())

    suspend fun request(packageName: String, minutes: Int): Boolean = mutex.withLock {
        if (packageName.isBlank() || bootCount() < 0 || !com.lucas.mobilemanager.domain.goals.TemporaryAccessPolicy.canRequest(
                minutes, lock.canModifyNow(), sessions.current()?.isRunning(time.now()) == true)) return@withLock false
        // Ne pas prolonger implicitement une dérogation par double clic.
        if (repository.temporaryAccess.first().any { it.packageName == packageName &&
                it.remaining(time.now(), bootCount(), SystemClock.elapsedRealtime()) != null }) return@withLock false
        val now = time.now()
        val duration = Duration.ofMinutes(minutes.toLong())
        repository.grant(TemporaryAccess(packageName, now, now + duration, bootCount(), SystemClock.elapsedRealtime() + duration.toMillis()))
        true
    }

    suspend fun revoke(packageName: String) { repository.revoke(packageName) }
}
