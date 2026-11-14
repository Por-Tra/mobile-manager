package com.lucas.mobilemanager.ui.mode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.mode.AppMode
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.lock.AppLock
import com.lucas.mobilemanager.ui.lock.LockBanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ModeViewModel @Inject constructor(
    private val appLock: AppLock,
    sessions: StrictSessionRepository,
    time: TimeProvider,
) : ViewModel() {
    val settings = appLock.modeSettings
    val lock = appLock.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sessionRunning = combine(sessions.observeCurrent(), flow {
        while (true) { emit(Unit); delay(1_000) }
    }) { session, _ -> session?.isRunning(time.now()) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    suspend fun select(mode: AppMode, wait: Int): Boolean = appLock.selectMode(mode, wait)
}

@Composable
fun ModeCard(viewModel: ModeViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lock by viewModel.lock.collectAsStateWithLifecycle()
    val session by viewModel.sessionRunning.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    val current = settings ?: return
    val editable = lock?.canModify == true && !session
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(current.selectedMode?.let(::modeName) ?: R.string.mode_not_selected), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(current.selectedMode?.let(::modeDescription) ?: R.string.mode_choose_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (current.selectedMode == AppMode.SEMI_FREE) {
                Text(stringResource(if (lock?.isSet == true) R.string.mode_pin_required else R.string.mode_delay_config, current.minimumWaitMinutes))
            }
            if (session) Text(stringResource(R.string.locked_during_session), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { choosing = true }, enabled = editable) { Text(stringResource(R.string.mode_choose)) }
            LockBanner()
        }
    }
    if (choosing) ModeDialog(
        initial = current.selectedMode,
        initialWait = current.minimumWaitMinutes,
        enabled = editable,
        onDismiss = { choosing = false },
        onSave = { mode, wait -> viewModel.select(mode, wait).also { if (it) choosing = false } },
    )
}

@Composable
private fun ModeDialog(initial: AppMode?, initialWait: Int, enabled: Boolean, onDismiss: () -> Unit, onSave: suspend (AppMode, Int) -> Boolean) {
    var mode by remember { mutableStateOf(initial) }
    var wait by remember { mutableStateOf(initialWait.toString()) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val minutes = wait.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mode_choose)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.mode_user_choice))
                AppMode.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = mode == option, enabled = enabled && !saving, role = Role.RadioButton, onClick = { mode = option }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == option, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(stringResource(modeName(option)), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(modeDescription(option)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (mode == AppMode.SEMI_FREE) {
                    OutlinedTextField(
                        value = wait,
                        onValueChange = { wait = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.mode_delay_label)) },
                        supportingText = { Text(stringResource(R.string.mode_delay_help)) },
                        isError = minutes !in 1..1440,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = enabled && !saving,
                    )
                    Text(stringResource(R.string.mode_configure_first), style = MaterialTheme.typography.bodySmall)
                }
                if (failed) Text(stringResource(R.string.mode_change_refused), color = MaterialTheme.colorScheme.tertiary)
            }
        },
        confirmButton = {
            TextButton(enabled = enabled && !saving && mode != null && (mode != AppMode.SEMI_FREE || minutes in 1..1440), onClick = {
                scope.launch {
                    saving = true
                    try { failed = !onSave(mode ?: return@launch, minutes?.coerceIn(1, 1440) ?: initialWait) }
                    finally { saving = false }
                }
            }) { Text(stringResource(R.string.mode_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

fun modeName(mode: AppMode): Int = when (mode) {
    AppMode.FREE -> R.string.mode_free
    AppMode.SEMI_FREE -> R.string.mode_semi_free
    AppMode.RESTRICTED -> R.string.mode_restricted
}

private fun modeDescription(mode: AppMode): Int = when (mode) {
    AppMode.FREE -> R.string.mode_free_body
    AppMode.SEMI_FREE -> R.string.mode_semi_free_body
    AppMode.RESTRICTED -> R.string.mode_restricted_body
}
