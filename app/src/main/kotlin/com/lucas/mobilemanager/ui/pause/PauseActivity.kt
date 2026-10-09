package com.lucas.mobilemanager.ui.pause

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.model.EventLogEntry
import com.lucas.mobilemanager.domain.model.EventType
import com.lucas.mobilemanager.domain.repository.EventLogRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.lucas.mobilemanager.ui.format.Formats
import com.lucas.mobilemanager.ui.theme.MobileManagerTheme
import kotlinx.coroutines.delay
import java.time.Duration

/** Ce que l'écran de pause affiche. */
data class PauseArgs(
    val packageName: String,
    val sessionLength: Duration,
    val pauseDuration: Duration,
    val goal: String,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, PauseActivity::class.java)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_SESSION_S, sessionLength.seconds)
            .putExtra(EXTRA_PAUSE_S, pauseDuration.seconds)
            .putExtra(EXTRA_GOAL, goal)

    companion object {
        fun from(intent: Intent): PauseArgs? = PauseArgs(
            packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return null,
            sessionLength = Duration.ofSeconds(intent.getLongExtra(EXTRA_SESSION_S, 0)),
            pauseDuration = Duration.ofSeconds(intent.getLongExtra(EXTRA_PAUSE_S, 10)),
            goal = intent.getStringExtra(EXTRA_GOAL).orEmpty(),
        )

        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_SESSION_S = "session_s"
        private const val EXTRA_PAUSE_S = "pause_s"
        private const val EXTRA_GOAL = "goal"
    }
}

/**
 * Écran de transition anti-doomscrolling : casse le geste automatique par une courte pause
 * de respiration. Ensuite, on peut fermer l'app (choix mis en avant) ou continuer.
 */
@AndroidEntryPoint
class PauseActivity : ComponentActivity() {

    @Inject lateinit var eventLogRepository: EventLogRepository
    @Inject lateinit var timeProvider: TimeProvider
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val args = PauseArgs.from(intent) ?: return goHome()
        val appLabel = appLabel(args.packageName)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )
        setContent {
            MobileManagerTheme {
                PauseScreen(
                    appLabel = appLabel,
                    args = args,
                    onCloseApp = { closeApp(args.packageName) },
                    onContinue = ::finishAndRemoveTask,
                )
            }
        }
    }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    /** « Fermer l'app » : compté dans le tableau de bord (pauses qui ont interrompu l'usage). */
    private fun closeApp(packageName: String) {
        val now = timeProvider.now()
        appScope.launch {
            eventLogRepository.record(
                EventLogEntry(type = EventType.PAUSE_CLOSED_APP, occurredAt = now, packageName = packageName),
            )
        }
        goHome()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }
}

@Composable
private fun PauseScreen(appLabel: String, args: PauseArgs, onCloseApp: () -> Unit, onContinue: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(args.pauseDuration.seconds.toInt()) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    val ready = secondsLeft == 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Breathing(Modifier.size(180.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (ready) R.string.pause_breathe_done else R.string.pause_breathe),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.pause_session, appLabel, Formats.duration(args.sessionLength)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (args.goal.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.pause_goal, args.goal),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(40.dp))
        Button(onClick = onCloseApp, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pause_close_app))
        }
        TextButton(onClick = onContinue, enabled = ready) {
            Text(if (ready) stringResource(R.string.pause_continue) else stringResource(R.string.pause_wait, secondsLeft))
        }
    }
}

/** Cercle qui grandit (inspiration) et rétrécit (expiration), 4 s chacun. */
@Composable
private fun Breathing(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val color = MaterialTheme.colorScheme.primaryContainer
    val ring = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val radius = size.minDimension / 2
        drawCircle(color = color, radius = radius * scale)
        drawCircle(color = ring.copy(alpha = 0.35f), radius = radius, style = Stroke(width = 3.dp.toPx()))
    }
}
