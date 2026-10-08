package com.lucas.mobilemanager

import android.app.Application
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.profiles.SeedDefaultProfiles
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MobileManagerApp : Application() {

    @Inject lateinit var seedDefaultProfiles: SeedDefaultProfiles

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedDefaultProfiles() }
    }
}
