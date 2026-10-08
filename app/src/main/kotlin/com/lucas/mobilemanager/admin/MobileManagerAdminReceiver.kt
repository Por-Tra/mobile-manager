package com.lucas.mobilemanager.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Administration de l'appareil : ajoute une étape avant la désinstallation en mode strict.
 * Ne rend pas la désinstallation impossible. La détection de la désactivation arrive en phase 4.
 */
class MobileManagerAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun component(context: Context) = ComponentName(context, MobileManagerAdminReceiver::class.java)
    }
}
