package com.lucas.mobilemanager.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.ui.theme.MobileManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Écran plein écran affiché à la place d'une app bloquée. Une seule sortie : l'accueil.
 * Pas de « encore 5 minutes », pas de désactivation depuis cet écran.
 */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    @Inject lateinit var timeProvider: TimeProvider

    private var args by mutableStateOf<BlockScreenArgs?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        args = BlockScreenArgs.from(intent)
        if (args == null) {
            goHome()
            return
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )
        setContent {
            MobileManagerTheme {
                args?.let {
                    BlockScreen(
                        args = it,
                        now = timeProvider.now(),
                        zone = timeProvider.zone(),
                        onGoHome = ::goHome,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        BlockScreenArgs.from(intent)?.let { args = it }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finishAndRemoveTask()
    }
}
