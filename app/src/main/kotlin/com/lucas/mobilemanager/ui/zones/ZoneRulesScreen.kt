package com.lucas.mobilemanager.ui.zones

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.BuildConfig
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.apps.AppIcon
import com.lucas.mobilemanager.ui.components.LIMIT_OPTIONS
import com.lucas.mobilemanager.ui.components.LimitPicker
import com.lucas.mobilemanager.ui.format.Formats
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneRulesScreen(
    onBack: () -> Unit,
    onOpenDefaultRules: () -> Unit,
    viewModel: ZoneRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zones_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Caption(stringResource(R.string.zones_intro)) }
            if (state.locked) item { Caption(stringResource(R.string.locked_during_session)) }
            items(state.zones, key = { "${it.packageName}/${it.zoneId}" }) { zone ->
                ZoneRow(zone, enabled = !state.locked, onSetting = { setting, minutes -> viewModel.setSetting(zone, setting, minutes) })
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.zones_apps_title), style = MaterialTheme.typography.titleSmall)
                    Caption(stringResource(R.string.zones_apps_body))
                    OutlinedButton(onClick = onOpenDefaultRules, enabled = !state.locked) {
                        Text(stringResource(R.string.zones_apps_open))
                    }
                }
            }
            item { Caption(stringResource(R.string.zones_fragile)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneRow(zone: ZoneItem, enabled: Boolean, onSetting: (ZoneSetting, Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(zone.packageName, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.zones_item, zone.appLabel, zone.zoneLabel), style = MaterialTheme.typography.bodyLarge)
                val used = Formats.duration(zone.usedToday)
                Caption(
                    if (zone.setting == ZoneSetting.QUOTA) {
                        stringResource(R.string.zones_used_of, used, Formats.duration(Duration.ofMinutes(zone.quotaMinutes.toLong())))
                    } else {
                        stringResource(R.string.zones_used, used)
                    },
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val settings = ZoneSetting.entries
            settings.forEachIndexed { index, setting ->
                SegmentedButton(
                    selected = zone.setting == setting,
                    onClick = { onSetting(setting, zone.quotaMinutes) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, settings.size),
                    label = { Text(stringResource(setting.label())) },
                )
            }
        }
        if (zone.setting == ZoneSetting.QUOTA) {
            LimitPicker(zone.quotaMinutes, enabled, onLimit = { onSetting(ZoneSetting.QUOTA, it) }, options = QUOTA_OPTIONS)
        }
    }
}

private fun ZoneSetting.label() = when (this) {
    ZoneSetting.BLOCKED -> R.string.rule_block
    ZoneSetting.QUOTA -> R.string.zones_quota
    ZoneSetting.ALLOWED -> R.string.rule_allow
}

/** En debug, 1 et 2 min pour tester un quota sans attendre. */
private val QUOTA_OPTIONS = if (BuildConfig.DEBUG) listOf(1, 2) + LIMIT_OPTIONS else LIMIT_OPTIONS

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
