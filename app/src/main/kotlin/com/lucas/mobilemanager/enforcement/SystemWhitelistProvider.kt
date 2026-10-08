package com.lucas.mobilemanager.enforcement

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.lucas.mobilemanager.domain.rules.SystemWhitelist
import com.lucas.mobilemanager.domain.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apps qui ne doivent jamais être bloquées. Résolues via le système plutôt que codées en dur,
 * car les noms de packages diffèrent selon les constructeurs (Samsung notamment).
 */
@Singleton
class SystemWhitelistProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) {
    private var cached: SystemWhitelist? = null
    private var cachedAt: Instant = Instant.MIN

    @Synchronized
    fun current(): SystemWhitelist {
        val now = timeProvider.now()
        val valid = cached?.takeIf { Duration.between(cachedAt, now) < CACHE_DURATION }
        if (valid != null) return valid
        return SystemWhitelist(resolve()).also {
            cached = it
            cachedAt = now
        }
    }

    private fun resolve(): Set<String> = buildSet {
        add(context.packageName)
        addAll(KNOWN_SYSTEM_PACKAGES)

        // Launcher : sans lui, impossible de revenir à l'accueil.
        addAll(handlersOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)))
        // Téléphone et appels.
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(::add)
        addAll(handlersOf(Intent(Intent.ACTION_DIAL)))
        // Horloge et réveil.
        addAll(handlersOf(Intent(AlarmClock.ACTION_SET_ALARM)))
        addAll(handlersOf(Intent(AlarmClock.ACTION_SHOW_ALARMS)))
        // Paramètres système.
        addAll(handlersOf(Intent(Settings.ACTION_SETTINGS)))
        // Claviers.
        context.getSystemService(InputMethodManager::class.java)
            ?.inputMethodList
            ?.forEach { add(it.packageName) }
    }

    private fun handlersOf(intent: Intent): List<String> {
        val packageManager = context.packageManager
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return infos.map { it.activityInfo.packageName }
    }

    private companion object {
        val CACHE_DURATION: Duration = Duration.ofMinutes(5)

        /** Composants système qui n'exposent pas d'intent public mais restent vitaux. */
        val KNOWN_SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.incallui",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.emergency",
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings",
        )
    }
}
