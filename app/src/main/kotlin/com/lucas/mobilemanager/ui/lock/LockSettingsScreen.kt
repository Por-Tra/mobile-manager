package com.lucas.mobilemanager.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.lock.LockPolicy
import kotlinx.coroutines.launch

/** Réglages › Code du proche : définir, modifier ou retirer le code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockSettingsScreen(onBack: () -> Unit, viewModel: LockViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var choosingPin by remember { mutableStateOf(false) }
    var confirmingRemoval by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val isSet = status?.isSet == true
    val canModify = status?.canModify == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lock_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.lock_intro), style = MaterialTheme.typography.bodyMedium)
            LockBanner(viewModel = viewModel)
            if (!isSet) {
                Button(onClick = { choosingPin = true }, enabled = status != null) { Text(stringResource(R.string.lock_set)) }
            } else {
                OutlinedButton(onClick = { choosingPin = true }, enabled = canModify) { Text(stringResource(R.string.lock_change)) }
                OutlinedButton(onClick = { confirmingRemoval = true }, enabled = canModify) { Text(stringResource(R.string.lock_remove)) }
            }
            message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            Caption(stringResource(R.string.lock_protects_settings))
            Caption(stringResource(R.string.lock_forgot))
            Caption(stringResource(R.string.lock_limits))
        }
    }

    if (choosingPin) {
        NewPinDialog(
            viewModel = viewModel,
            onDismiss = { choosingPin = false },
            onSaved = {
                choosingPin = false
                message = R.string.lock_saved
            },
        )
    }
    if (confirmingRemoval) {
        AlertDialog(
            onDismissRequest = { confirmingRemoval = false },
            title = { Text(stringResource(R.string.lock_remove)) },
            text = { Text(stringResource(R.string.lock_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePin { removed -> if (removed) message = R.string.lock_removed }
                    confirmingRemoval = false
                }) { Text(stringResource(R.string.lock_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmingRemoval = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Nouveau code saisi deux fois. */
@Composable
private fun NewPinDialog(viewModel: LockViewModel, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    PinDialog(
        title = stringResource(R.string.lock_title),
        intro = stringResource(R.string.lock_new_intro),
        fields = listOf(stringResource(R.string.lock_new_pin), stringResource(R.string.lock_confirm_pin)),
        error = error,
        checking = checking,
        onDismiss = onDismiss,
        onSubmit = { (pin, confirmation) ->
            when {
                !LockPolicy.isValidPin(pin) -> error = resources.getString(R.string.lock_invalid)
                pin != confirmation -> error = resources.getString(R.string.lock_mismatch)
                else -> {
                    checking = true
                    scope.launch {
                        if (viewModel.setPin(pin)) onSaved() else error = resources.getString(R.string.lock_banner_locked)
                        checking = false
                    }
                }
            }
        },
    )
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
