package com.lucas.mobilemanager.session

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.theme.LocalExtendedColors
import com.lucas.mobilemanager.ui.theme.MobileManagerTheme
import java.time.Instant
import java.time.ZoneId

/** Écran affiché si la surveillance est coupée pendant une session. Informatif, sans reproche. */
class ProtectionAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val endsAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_ENDS_AT, 0))
        val endTime = Formats.TIME.format(endsAt.atZone(ZoneId.systemDefault()))
        setContent {
            MobileManagerTheme {
                val colors = LocalExtendedColors.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.limitReachedContainer)
                        .safeDrawingPadding()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.protection_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onLimitReachedContainer,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.protection_body, endTime),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onLimitReachedContainer,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    Button(
                        onClick = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            finish()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.onLimitReachedContainer,
                            contentColor = colors.limitReachedContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.protection_reenable)) }
                    TextButton(onClick = ::finish) {
                        Text(stringResource(R.string.protection_dismiss), color = colors.onLimitReachedContainer)
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ENDS_AT = "ends_at"

        fun intent(context: Context, endsAt: Instant): Intent =
            Intent(context, ProtectionAlertActivity::class.java).putExtra(EXTRA_ENDS_AT, endsAt.toEpochMilli())
    }
}
