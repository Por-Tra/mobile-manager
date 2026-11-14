package com.lucas.mobilemanager.block

import android.content.Intent
import android.os.Bundle
import com.lucas.mobilemanager.locale.LocalizedActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.lucas.mobilemanager.ui.theme.enableMobileManagerEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.MainActivity
import com.lucas.mobilemanager.domain.mode.AppMode
import com.lucas.mobilemanager.domain.mode.ModePolicy
import com.lucas.mobilemanager.lock.AppLock
import com.lucas.mobilemanager.ui.lock.LockBanner
import com.lucas.mobilemanager.R
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.lucas.mobilemanager.ui.theme.MobileManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Écran affiché à la place d'une app bloquée. En semi-libre, propose le déverrouillage
 * des réglages par code ou attente ; aucune restriction n'est levée automatiquement.
 */
@AndroidEntryPoint
class BlockActivity : LocalizedActivity() {

    @Inject lateinit var timeProvider: TimeProvider
    @Inject lateinit var appLock: AppLock

    private var args by mutableStateOf<BlockScreenArgs?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMobileManagerEdgeToEdge()
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
                val mode by appLock.modeSettings.collectAsStateWithLifecycle()
                LaunchedEffect(mode) {
                    if (mode?.let(ModePolicy::blocksEnabled) == false) finishAndRemoveTask()
                }
                args?.let {
                    BlockScreen(
                        args = it,
                        now = timeProvider.now(),
                        zone = timeProvider.zone(),
                        onGoHome = ::goHome,
                        actions = {
                            if (mode?.selectedMode == AppMode.SEMI_FREE) {
                                LockBanner()
                                TextButton(onClick = {
                                    startActivity(Intent(this@BlockActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }) { Text(stringResource(R.string.mode_open_manager)) }
                            }
                        },
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
