package com.lucas.mobilemanager.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.goals.TemporaryAccessManager
import kotlinx.coroutines.launch

@Composable
fun TemporaryAccessAction(packageName: String, manager: TemporaryAccessManager, enabled: Boolean, onGranted: () -> Unit = {}) {
    var dialog by rememberSaveable(packageName) { mutableStateOf(false) }
    var minutes by rememberSaveable(packageName) { mutableStateOf("5") }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    TextButton(onClick = { failed = false; dialog = true }, enabled = enabled && packageName.isNotBlank()) {
        Text(stringResource(R.string.temporary_request))
    }
    if (dialog) AlertDialog(
        onDismissRequest = { if (!saving) dialog = false },
        title = { Text(stringResource(R.string.temporary_request)) },
        text = {
            Column {
                Text(stringResource(R.string.temporary_body))
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.goal_minutes)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, isError = minutes.toIntOrNull() !in 1..1440)
                if (failed) Text(stringResource(R.string.goal_action_refused))
            }
        },
        confirmButton = { TextButton(enabled = enabled && !saving && minutes.toIntOrNull() in 1..1440, onClick = {
            saving = true
            scope.launch {
                try {
                    if (manager.request(packageName, minutes.toInt())) { dialog = false; onGranted() } else failed = true
                } finally { saving = false }
            }
        }) { Text(stringResource(R.string.temporary_start)) } },
        dismissButton = { TextButton(onClick = { dialog = false }, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}
