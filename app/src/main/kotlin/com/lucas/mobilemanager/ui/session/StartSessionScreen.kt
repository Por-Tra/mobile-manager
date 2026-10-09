package com.lucas.mobilemanager.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy
import com.lucas.mobilemanager.domain.session.StrictSessionPolicy.ValidationError
import com.lucas.mobilemanager.ui.apps.AppSearchField
import com.lucas.mobilemanager.ui.apps.appPickerItems
import com.lucas.mobilemanager.ui.components.TimePickerDialog
import com.lucas.mobilemanager.ui.format.Formats
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartSessionScreen(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    viewModel: StartSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingTime by remember { mutableStateOf(false) }
    val endTime = Formats.TIME.format(viewModel.endsAt(state.length).atZone(ZoneId.systemDefault()))

    LaunchedEffect(state.started) { if (state.started) onStarted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.session_start_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                state.errors.forEach { Text(errorText(it), color = MaterialTheme.colorScheme.tertiary) }
                if (state.alreadyRunning) {
                    Text(stringResource(R.string.session_already_running), color = MaterialTheme.colorScheme.tertiary)
                }
                Button(
                    onClick = viewModel::askConfirmation,
                    enabled = state.selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.session_start_button)) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { SectionTitle(R.string.session_duration) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionLength.PRESETS.forEach { preset ->
                        FilterChip(
                            selected = state.length == preset,
                            onClick = { viewModel.setLength(preset) },
                            label = { Text(Formats.duration(preset.duration)) },
                        )
                    }
                    val until = state.length as? SessionLength.Until
                    FilterChip(
                        selected = until != null,
                        onClick = { pickingTime = true },
                        label = {
                            Text(
                                if (until != null) {
                                    stringResource(R.string.session_until_time, Formats.time(until.time))
                                } else {
                                    stringResource(R.string.session_until)
                                },
                            )
                        },
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.session_ends_at, endTime),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item { SectionTitle(R.string.session_reflection_delay) }
            item {
                Text(
                    stringResource(R.string.session_reflection_delay_value, state.reflectionDelayMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val min = StrictSessionPolicy.MIN_REFLECTION_DELAY.toMinutes().toInt()
                val max = StrictSessionPolicy.MAX_REFLECTION_DELAY.toMinutes().toInt()
                Slider(
                    value = state.reflectionDelayMinutes.toFloat(),
                    onValueChange = { viewModel.setReflectionDelay(it.toInt()) },
                    valueRange = min.toFloat()..max.toFloat(),
                    steps = max - min - 1,
                )
                Text(
                    stringResource(R.string.session_reflection_delay_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                SectionTitle(R.string.session_apps)
                Text(
                    pluralStringResource(R.plurals.session_apps_selected, state.selected.size, state.selected.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { AppSearchField(state.query, viewModel::setQuery, Modifier.padding(vertical = 4.dp)) }
            if (state.loadingApps) {
                item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            } else {
                appPickerItems(state.apps, state.selected, state.query, viewModel::toggle)
            }
        }
    }

    if (pickingTime) {
        TimePickerDialog(
            title = stringResource(R.string.session_until_pick),
            initial = LocalTime.now().plusHours(1).withMinute(0),
            onDismiss = { pickingTime = false },
            onConfirm = {
                viewModel.setLength(SessionLength.Until(it))
                pickingTime = false
            },
        )
    }

    if (state.confirming) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(stringResource(R.string.session_confirm_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.session_confirm_body,
                        state.selected.size,
                        endTime,
                        state.selected.size,
                        state.reflectionDelayMinutes,
                    ),
                )
            },
            confirmButton = { Button(onClick = viewModel::start) { Text(stringResource(R.string.session_confirm)) } },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun errorText(error: ValidationError): String = when (error) {
    ValidationError.END_NOT_IN_FUTURE -> stringResource(R.string.session_error_end)
    ValidationError.TOO_LONG -> stringResource(R.string.session_error_too_long)
    ValidationError.NO_APPS -> stringResource(R.string.session_error_no_apps)
    ValidationError.DELAY_OUT_OF_RANGE -> stringResource(R.string.session_error_delay)
}
