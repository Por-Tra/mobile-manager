package com.lucas.mobilemanager.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.admin.MobileManagerAdminReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UninstallPreference(enabled: Boolean, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.uninstall_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.uninstall_message), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.uninstall_body), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!enabled) Text(stringResource(R.string.uninstall_locked), style = MaterialTheme.typography.bodySmall)
        if (failed) Text(stringResource(R.string.uninstall_failed), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = enabled && !busy, onClick = {
            scope.launch {
                busy = true
                failed = false
                try {
                    if (!viewModel.canUninstall()) { failed = true; return@launch }
                    if (!removeOwnAdmin(context)) { failed = true; return@launch }
                    if (!viewModel.canUninstall()) { failed = true; return@launch }
                    context.startActivity(Intent(Intent.ACTION_DELETE, "package:${context.packageName}".toUri()))
                } catch (_: ActivityNotFoundException) {
                    failed = true
                } catch (_: SecurityException) {
                    failed = true
                } finally {
                    busy = false
                }
            }
        }) { Text(stringResource(if (busy) R.string.uninstall_preparing else R.string.uninstall_action)) }
    }
}

private suspend fun removeOwnAdmin(context: Context): Boolean {
    val manager = context.getSystemService(DevicePolicyManager::class.java)
    val component = MobileManagerAdminReceiver.component(context)
    if (!manager.isAdminActive(component)) return true
    manager.removeActiveAdmin(component)
    repeat(20) {
        if (!manager.isAdminActive(component)) return true
        delay(100)
    }
    return !manager.isAdminActive(component)
}
