package com.lucas.mobilemanager.enforcement

import android.content.Context
import android.widget.Toast
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.rules.Decision
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.ui.format.Formats
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Préavis discret, au plus une fois par app/zone et par jour, sur le thread principal. */
@Singleton
class LimitWarningNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: EnforcementCoordinator,
    private val timeProvider: TimeProvider,
) {
    private val policy = com.lucas.mobilemanager.domain.rules.LimitWarningPolicy()

    fun showIfNeeded(packageName: String, decision: Decision.Allowed, zone: String? = null) {
        val key = "$packageName/${zone.orEmpty()}"
        if (!policy.shouldWarn(timeProvider.localNow().toLocalDate(), key, decision.remainingToday,
                coordinator.preferences.value?.limitWarningsEnabled == true && coordinator.blocksEnabledNow())) return
        val remaining = requireNotNull(decision.remainingToday)
        val label = try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { packageName }
        Toast.makeText(context, context.getString(R.string.limit_warning, zone?.let { "$label · $it" } ?: label,
            Formats.duration(remaining)), Toast.LENGTH_LONG).show()
    }
}
