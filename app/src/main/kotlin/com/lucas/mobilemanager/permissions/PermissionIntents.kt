package com.lucas.mobilemanager.permissions

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.admin.MobileManagerAdminReceiver
import com.lucas.mobilemanager.domain.onboarding.PermissionKind

/** Intents qui ouvrent l'écran système permettant d'accorder chaque permission. */
object PermissionIntents {

    /** `null` pour les notifications : elles passent par une demande runtime. */
    @SuppressLint("BatteryLife")
    fun forPermission(context: Context, kind: PermissionKind): Intent? = when (kind) {
        PermissionKind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        PermissionKind.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        PermissionKind.BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
        PermissionKind.OVERLAY ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))
        PermissionKind.NOTIFICATIONS -> null
        PermissionKind.DEVICE_ADMIN ->
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    MobileManagerAdminReceiver.component(context),
                )
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.device_admin_explanation),
                )
    }

    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    fun appNotificationSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun batterySettings() = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)

    /** Lance l'intent ; si l'écran n'existe pas sur ce téléphone, ouvre les infos de l'app. */
    fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(appDetails(context))
        }
    }

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()
}
