package com.lucas.mobilemanager

import android.os.Bundle
import android.content.Intent
import com.lucas.mobilemanager.locale.LocalizedActivity
import androidx.activity.compose.setContent
import com.lucas.mobilemanager.ui.theme.enableMobileManagerEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.navigation.MobileManagerNavHost
import com.lucas.mobilemanager.ui.theme.MobileManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : LocalizedActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var openGoalsRequest by mutableIntStateOf(0)
    private var openProtectionTestRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMobileManagerEdgeToEdge()
        // Ouverture depuis le bandeau du mode diagnostic, après une capture.
        val openDiagnostic = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_DIAGNOSTIC, false)
        if (savedInstanceState == null && intent.getBooleanExtra("open_goals", false)) openGoalsRequest++
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_PROTECTION_TEST, false)) openProtectionTestRequest++
        setContent {
            MobileManagerTheme {
                val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    startDestination?.let { MobileManagerNavHost(startDestination = it, openDiagnostic = openDiagnostic,
                        openGoalsRequest = openGoalsRequest, openProtectionTestRequest = openProtectionTestRequest) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_goals", false)) openGoalsRequest++
        if (intent.getBooleanExtra(EXTRA_OPEN_PROTECTION_TEST, false)) openProtectionTestRequest++
    }

    companion object {
        const val EXTRA_OPEN_DIAGNOSTIC = "open_diagnostic"
        const val EXTRA_OPEN_PROTECTION_TEST = "open_protection_test"
    }
}
