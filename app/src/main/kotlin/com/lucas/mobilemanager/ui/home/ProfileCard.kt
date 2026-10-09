package com.lucas.mobilemanager.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.profiles.ManualDuration
import com.lucas.mobilemanager.ui.components.TimePickerDialog
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.format.profileSummary
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun ProfileCard(
    item: ProfileItem,
    editable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    profileSummary(item.profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val badge = when {
                    item.manual && item.manualUntil != null -> stringResource(
                        R.string.profile_manual_until,
                        Formats.TIME.format(item.manualUntil.atZone(ZoneId.systemDefault())),
                    )
                    item.manual -> stringResource(R.string.profile_manual)
                    item.activeNow -> stringResource(R.string.profile_active_now)
                    else -> null
                }
                badge?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (item.manual && editable) {
                    TextButton(onClick = onDeactivate, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.profile_manual_deactivate))
                    }
                }
            }
            Switch(checked = item.profile.enabled, onCheckedChange = onToggle, enabled = editable)
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = editable) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.profile_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    fun close(action: () -> Unit) = { menuOpen = false; action() }
                    if (item.manual) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_manual_deactivate)) },
                            onClick = close(onDeactivate),
                        )
                    } else {
                        DropdownMenuItem(text = { Text(stringResource(R.string.profile_activate_now)) }, onClick = close(onActivate))
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.profile_edit)) }, onClick = close(onOpen))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_move_up)) },
                        enabled = canMoveUp,
                        onClick = close(onMoveUp),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_move_down)) },
                        enabled = canMoveDown,
                        onClick = close(onMoveDown),
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.profile_delete)) }, onClick = close(onDelete))
                }
            }
        }
    }
}

/** Choix de la durée d'une activation manuelle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualActivationSheet(
    profile: Profile,
    replacing: Profile?,
    onDismiss: () -> Unit,
    onChoose: (ManualDuration) -> Unit,
) {
    var pickingTime by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.manual_sheet_title, profile.name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                replacing?.let { stringResource(R.string.manual_sheet_replaces, it.name) }
                    ?: stringResource(R.string.manual_sheet_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Option(stringResource(R.string.manual_one_hour)) { onChoose(ManualDuration.ONE_HOUR) }
            Option(stringResource(R.string.manual_two_hours)) { onChoose(ManualDuration.TWO_HOURS) }
            Option(stringResource(R.string.manual_until)) { pickingTime = true }
            Option(stringResource(R.string.manual_until_turned_off)) { onChoose(ManualDuration.UntilTurnedOff) }
        }
    }
    if (pickingTime) {
        TimePickerDialog(
            title = stringResource(R.string.manual_until),
            initial = LocalTime.now().plusHours(1).withMinute(0),
            onDismiss = { pickingTime = false },
            onConfirm = {
                pickingTime = false
                onChoose(ManualDuration.Until(it))
            },
        )
    }
}

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(text) }, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp))
}
