package com.lucas.mobilemanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lucas.mobilemanager.dashboard.DailyUsageSnapshotter
import com.lucas.mobilemanager.dashboard.DailyUsageWorker
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.profiles.SeedDefaultProfiles
import com.lucas.mobilemanager.domain.zones.SeedDefaultAppBlocks
import com.lucas.mobilemanager.domain.zones.SeedDefaultZoneRules
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltAndroidApp
class MobileManagerApp : Application(), Configuration.Provider {

    @Inject lateinit var seedDefaultProfiles: SeedDefaultProfiles

    @Inject lateinit var seedDefaultAppBlocks: SeedDefaultAppBlocks

    @Inject lateinit var seedDefaultZoneRules: SeedDefaultZoneRules

    @Inject lateinit var dailyUsageSnapshotter: DailyUsageSnapshotter

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var goals: com.lucas.mobilemanager.domain.goals.GoalRepository
    @Inject lateinit var time: com.lucas.mobilemanager.domain.time.TimeProvider

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    /** WorkManager initialisé à la demande avec les workers Hilt (initialiseur par défaut retiré du manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.lucas.mobilemanager.locale.AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            seedDefaultProfiles()
            seedDefaultAppBlocks()
            seedDefaultZoneRules()
            dailyUsageSnapshotter.ensureSnapshots()
        }
        DailyUsageWorker.schedule(this)
        appScope.launch {
            goals.goals.map { all -> all.any { it.remindersEnabled && it.isActive(time.localNow().toLocalDate()) && it.slots.isNotEmpty() } }
                .distinctUntilChanged().collect { enabled ->
                    if (enabled) com.lucas.mobilemanager.goals.GoalReminderWorker.schedule(this@MobileManagerApp)
                    else androidx.work.WorkManager.getInstance(this@MobileManagerApp).cancelUniqueWork("goal_preparation")
                }
        }
    }
}
