package com.lucas.mobilemanager.enforcement

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.lucas.mobilemanager.ui.theme.BackgroundLight
import com.lucas.mobilemanager.ui.theme.BackgroundDark
import com.lucas.mobilemanager.ui.theme.OnSurfaceLight
import com.lucas.mobilemanager.ui.theme.OnSurfaceDark
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lucas.mobilemanager.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Superposition tactile : aucun lancement de l'app et aucune permission de superposition requise. */
class OpeningPauseOverlay(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var timer: Job? = null
    private val state = com.lucas.mobilemanager.domain.rules.OpeningPauseState()
    val isShowing: Boolean get() = view != null

    fun onForeground(packageName: String?) {
        if (state.onForeground(packageName)) clear()
    }

    fun show(packageName: String, seconds: Int, onContinue: () -> Unit) {
        if (!state.needsPause(packageName) || view != null) return
        val density = service.resources.displayMetrics.density
        val dark = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val background = (if (dark) BackgroundDark else BackgroundLight).toArgb()
        val foreground = (if (dark) OnSurfaceDark else OnSurfaceLight).toArgb()
        val layout = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((28 * density).toInt(), 0, (28 * density).toInt(), 0)
            setBackgroundColor(background)
        }
        val title = TextView(service).apply {
            text = service.getString(R.string.opening_pause_title)
            textSize = 26f
            setTextColor(foreground)
        }
        val body = TextView(service).apply {
            text = service.getString(R.string.opening_pause_body)
            textSize = 18f
            setTextColor(foreground)
        }
        val proceed = Button(service).apply {
            isEnabled = false
            setOnClickListener {
                state.continueOpening(packageName)
                clear()
                onContinue()
            }
        }
        val home = Button(service).apply {
            text = service.getString(R.string.block_go_home)
            setOnClickListener {
                clear()
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }
        layout.addView(title); layout.addView(body); layout.addView(proceed); layout.addView(home)
        manager.addView(layout, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ))
        view = layout
        timer = scope.launch {
            for (remaining in seconds downTo 1) {
                proceed.text = service.getString(R.string.opening_pause_countdown, remaining)
                delay(1000)
            }
            proceed.text = service.getString(R.string.opening_pause_continue)
            proceed.isEnabled = true
        }
    }

    fun clear() {
        timer?.cancel(); timer = null
        view?.let(manager::removeView)
        view = null
    }

    fun reset() { clear(); state.reset() }
}
