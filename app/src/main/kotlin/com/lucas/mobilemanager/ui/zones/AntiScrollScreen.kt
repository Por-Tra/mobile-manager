package com.lucas.mobilemanager.ui.zones

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.components.PageIntro
import com.lucas.mobilemanager.ui.components.PageTopBar
import com.lucas.mobilemanager.ui.settings.SettingsViewModel
import com.lucas.mobilemanager.ui.settings.WellbeingPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiScrollScreen(onOpenDefaultRules: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val session by viewModel.sessionRunning.collectAsStateWithLifecycle()
    val lock by viewModel.lockStatus.collectAsStateWithLifecycle()
    Scaffold(topBar = { PageTopBar(stringResource(R.string.antiscroll_title)) }) { padding ->
        Column(Modifier.padding(padding)) {
            SecondaryTabRow(selectedTabIndex = section, containerColor = MaterialTheme.colorScheme.background) {
                listOf(R.string.antiscroll_zones, R.string.antiscroll_habits).forEachIndexed { index, label ->
                    Tab(selected = section == index, onClick = { section = index }, text = { Text(stringResource(label)) })
                }
            }
            if (section == 0) {
                ZoneRulesScreen(onBack = {}, onOpenDefaultRules = onOpenDefaultRules, showTopBar = false)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PageIntro(stringResource(R.string.habits_heading), stringResource(R.string.habits_intro))
                    WellbeingPreferences(viewModel, enabled = !session && lock?.canModify == true)
                }
            }
        }
    }
}
