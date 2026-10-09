package com.lucas.mobilemanager.session

import android.content.Context
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.session.StrictSessionManager
import com.lucas.mobilemanager.domain.time.TimeProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/**
 * Accès aux dépendances depuis les BroadcastReceiver système (démarrage, admin de l'appareil),
 * qui ne peuvent pas recevoir d'injection Hilt simplement.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SessionEntryPoint {
    fun strictSessionRepository(): StrictSessionRepository
    fun strictSessionManager(): StrictSessionManager
    fun timeProvider(): TimeProvider

    @ApplicationScope
    fun appScope(): CoroutineScope

    companion object {
        fun from(context: Context): SessionEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, SessionEntryPoint::class.java)
    }
}
