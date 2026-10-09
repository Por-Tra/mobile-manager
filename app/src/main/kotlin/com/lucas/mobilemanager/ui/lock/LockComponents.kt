package com.lucas.mobilemanager.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.lock.LockPolicy
import com.lucas.mobilemanager.lock.AppLock
import com.lucas.mobilemanager.lock.LockStatus
import com.lucas.mobilemanager.ui.format.Formats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Accès au code du proche depuis n'importe quel écran (une instance par écran de navigation). */
@HiltViewModel
class LockViewModel @Inject constructor(private val appLock: AppLock) : ViewModel() {

    /** `null` tant que l'état n'est pas lu : l'écran reste alors en lecture seule. */
    val status: StateFlow<LockStatus?> = appLock.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun unlock(pin: String): LockPolicy.Attempt = appLock.unlock(pin)

    fun lockNow() = appLock.lockNow()

    suspend fun setPin(pin: String): Boolean = appLock.setPin(pin)

    fun removePin(onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(appLock.removePin()) }
    }
}

/** Les restrictions sont-elles modifiables sur cet écran (pas de code, ou déverrouillé) ? */
@Composable
fun rememberCanModify(viewModel: LockViewModel = hiltViewModel()): Boolean {
    val status by viewModel.status.collectAsStateWithLifecycle()
    return status?.canModify == true
}

/**
 * Bandeau « Protégé par le code du proche » + « Déverrouiller », ou « Déverrouillé jusqu'à… ».
 * N'affiche rien quand aucun code n'est défini.
 */
@Composable
fun LockBanner(modifier: Modifier = Modifier, viewModel: LockViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val current = status ?: return
    if (!current.isSet) return
    var unlocking by remember { mutableStateOf(false) }
    val unlockedUntil = current.unlockedUntil
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (unlockedUntil == null) Icons.Outlined.Lock else Icons.Outlined.LockOpen, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (unlockedUntil == null) {
                    Text(stringResource(R.string.lock_banner_locked), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.lock_banner_unlocked, time(unlockedUntil)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (unlockedUntil == null) {
                TextButton(onClick = { unlocking = true }) { Text(stringResource(R.string.lock_unlock)) }
            } else {
                TextButton(onClick = viewModel::lockNow) { Text(stringResource(R.string.lock_lock_now)) }
            }
        }
    }
    if (unlocking) UnlockDialog(onDismiss = { unlocking = false }, viewModel = viewModel)
}

/** Saisie du code du proche. Se ferme d'elle-même quand le code est bon. */
@Composable
fun UnlockDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit = {}, viewModel: LockViewModel = hiltViewModel()) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    PinDialog(
        title = stringResource(R.string.lock_dialog_title),
        fields = listOf(stringResource(R.string.lock_dialog_hint)),
        error = error,
        checking = checking,
        onDismiss = onDismiss,
        onSubmit = { values ->
            checking = true
            scope.launch {
                when (val result = viewModel.unlock(values.single())) {
                    LockPolicy.Attempt.Success -> {
                        onUnlocked()
                        onDismiss()
                    }
                    is LockPolicy.Attempt.Wrong -> error = resources.getString(R.string.lock_wrong, result.attemptsLeft)
                    is LockPolicy.Attempt.LockedOut -> error = resources.getString(R.string.lock_locked_out, time(result.until))
                }
                checking = false
            }
        },
    )
}

/** Boîte de saisie de un ou deux codes (chiffres masqués). */
@Composable
fun PinDialog(
    title: String,
    fields: List<String>,
    error: String?,
    checking: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (List<String>) -> Unit,
    intro: String? = null,
) {
    var values by remember { mutableStateOf(List(fields.size) { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                intro?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                fields.forEachIndexed { index, label ->
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(LockPolicy.PIN_LENGTH.last)
                            values = values.toMutableList().also { it[index] = digits }
                        },
                        label = { Text(label) },
                        singleLine = true,
                        enabled = !checking,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                when {
                    checking -> Text(stringResource(R.string.lock_checking), style = MaterialTheme.typography.bodySmall)
                    error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(values) },
                enabled = !checking && values.all { it.length >= LockPolicy.PIN_LENGTH.first },
            ) { Text(stringResource(R.string.lock_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun time(instant: Instant): String = Formats.time(instant.atZone(ZoneId.systemDefault()).toLocalTime())
