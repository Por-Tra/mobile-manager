package com.lucas.mobilemanager.permissions

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.lucas.mobilemanager.admin.MobileManagerAdminReceiver
import com.lucas.mobilemanager.domain.onboarding.PermissionKind
import com.lucas.mobilemanager.domain.onboarding.PermissionStatus
import com.lucas.mobilemanager.service.MonitorAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lit l'état réel de chaque permission auprès du système. Aucun cache : appeler à chaque reprise. */
@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun currentStatuses(): Map<PermissionKind, PermissionStatus> =
        PermissionKind.entries.associateWith(::status)

    fun status(kind: PermissionKind): PermissionStatus = when (kind) {
        PermissionKind.ACCESSIBILITY -> isAccessibilityServiceEnabled().toStatus()
        PermissionKind.USAGE_STATS -> hasUsageStatsAccess().toStatus()
        PermissionKind.BATTERY_OPTIMIZATION -> isIgnoringBatteryOptimizations().toStatus()
        PermissionKind.OVERLAY -> Settings.canDrawOverlays(context).toStatus()
        PermissionKind.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(context).areNotificationsEnabled().toStatus()
            } else {
                PermissionStatus.NOT_APPLICABLE
            }
        PermissionKind.DEVICE_ADMIN -> isDeviceAdminActive().toStatus()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val expected = ComponentName(context, MonitorAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    // Déprécié en API 37, mais la surcharge de remplacement n'existe pas sur minSdk 29.
    @Suppress("DEPRECATION")
    private fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    private fun isDeviceAdminActive(): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)
            .isAdminActive(MobileManagerAdminReceiver.component(context))

    private fun Boolean.toStatus() = if (this) PermissionStatus.GRANTED else PermissionStatus.MISSING
}
