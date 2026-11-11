package com.lucas.mobilemanager.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.locale.AppLanguage
import com.lucas.mobilemanager.update.UpdateState
import com.lucas.mobilemanager.update.UpdateViewModel

@Composable
fun LanguagePreference() {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf(false) }
    val selected = AppLanguage.selected(context)
    val options = listOf("" to stringResource(R.string.language_system), "fr" to "Français", "en" to "English")
    ListItem(
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        headlineContent = { Text(stringResource(R.string.language_title)) },
        supportingContent = { Text(options.firstOrNull { it.first == selected }?.second ?: options.first().second) },
        leadingContent = { Icon(Icons.Outlined.Language, null) },
        modifier = Modifier.clickable { dialog = true },
    )
    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            Column {
                options.forEach { (tag, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected == tag, role = Role.RadioButton, onClick = {
                            dialog = false
                            context.activity()?.let { AppLanguage.set(it, tag) }
                        }).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected == tag, onClick = null)
                        Text(label, Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { dialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun UpdatePreference(enabled: Boolean, viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.update_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val message = when (state) {
            UpdateState.Idle -> R.string.update_source
            UpdateState.Checking -> R.string.update_checking
            UpdateState.Current -> R.string.update_current
            UpdateState.Unpublished -> R.string.update_unpublished
            UpdateState.Incompatible -> R.string.update_incompatible
            UpdateState.Failed -> R.string.update_failed
            UpdateState.Restricted -> R.string.update_restricted
            is UpdateState.Available -> R.string.update_available
            is UpdateState.Downloading -> R.string.update_downloading
            is UpdateState.Ready -> R.string.update_ready
        }
        Text(
            when (val value = state) {
                is UpdateState.Available -> stringResource(message, value.release.version)
                is UpdateState.Downloading -> stringResource(message, value.percent)
                else -> stringResource(message)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (!enabled) Text(stringResource(R.string.update_restricted), style = MaterialTheme.typography.bodySmall)
        if (state is UpdateState.Checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        (state as? UpdateState.Downloading)?.let { LinearProgressIndicator(progress = { it.percent / 100f }, modifier = Modifier.fillMaxWidth()) }
        Button(
            enabled = enabled && !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                when (val value = state) {
                    is UpdateState.Available -> viewModel.download(value.release)
                    is UpdateState.Ready -> viewModel.install { file ->
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                        } else {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }
                    }
                    else -> viewModel.check()
                }
            },
        ) {
            Text(stringResource(when (state) {
                is UpdateState.Available -> R.string.update_download
                is UpdateState.Ready -> R.string.update_install
                else -> R.string.update_check
            }))
        }
    }
}
