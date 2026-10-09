package com.lucas.mobilemanager.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.lucas.mobilemanager.enforcement.SystemWhitelistProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val packageName: String, val label: String)

/** Apps lançables qu'on peut choisir de bloquer (sans Mobile Manager ni les apps vitales). */
@Singleton
class InstalledApps @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whitelistProvider: SystemWhitelistProvider,
) {
    /** Nom affiché d'une app, ou `null` si elle n'est pas installée. */
    suspend fun label(packageName: String): String? = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    suspend fun blockable(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
        val whitelist = whitelistProvider.current()
        val collator = Collator.getInstance(Locale.FRENCH)
        activities
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName !in whitelist }
            .map { InstalledApp(it.packageName, packageManager.getApplicationLabel(it).toString()) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
}
