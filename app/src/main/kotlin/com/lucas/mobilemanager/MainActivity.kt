package com.lucas.mobilemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.mobilemanager.navigation.MobileManagerNavHost
import com.lucas.mobilemanager.ui.theme.MobileManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ouverture depuis le bandeau du mode diagnostic, après une capture.
        val openDiagnostic = savedInstanceState == null && intent.getBooleanExtra(EXTRA_OPEN_DIAGNOSTIC, false)
        setContent {
            MobileManagerTheme {
                val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    startDestination?.let { MobileManagerNavHost(startDestination = it, openDiagnostic = openDiagnostic) }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_DIAGNOSTIC = "open_diagnostic"
    }
}
