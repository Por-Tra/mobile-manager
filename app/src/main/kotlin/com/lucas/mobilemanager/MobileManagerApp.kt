package com.lucas.mobilemanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lucas.mobilemanager.dashboard.DailyUsageSnapshotter
import com.lucas.mobilemanager.dashboard.DailyUsageWorker
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.doomscroll.SeedDefaultScrollGuards
import com.lucas.mobilemanager.domain.profiles.SeedDefaultProfiles
import com.lucas.mobilemanager.domain.zones.SeedDefaultZoneRules
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MobileManagerApp : Application(), Configuration.Provider {

    @Inject lateinit var seedDefaultProfiles: SeedDefaultProfiles

    @Inject lateinit var seedDefaultScrollGuards: SeedDefaultScrollGuards

    @Inject lateinit var seedDefaultZoneRules: SeedDefaultZoneRules

    @Inject lateinit var dailyUsageSnapshotter: DailyUsageSnapshotter

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    /** WorkManager initialisé à la demande avec les workers Hilt (initialiseur par défaut retiré du manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            seedDefaultProfiles()
            seedDefaultScrollGuards()
            seedDefaultZoneRules()
            dailyUsageSnapshotter.ensureSnapshots()
        }
        DailyUsageWorker.schedule(this)
    }
}
