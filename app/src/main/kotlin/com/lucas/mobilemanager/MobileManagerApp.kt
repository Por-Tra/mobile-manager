package com.lucas.mobilemanager

import android.app.Application
import com.lucas.mobilemanager.domain.profiles.SeedDefaultProfiles
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MobileManagerApp : Application() {

    @Inject lateinit var seedDefaultProfiles: SeedDefaultProfiles

    /** Portée de l'application, pour les tâches qui ne dépendent d'aucun écran. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedDefaultProfiles() }
    }
}
