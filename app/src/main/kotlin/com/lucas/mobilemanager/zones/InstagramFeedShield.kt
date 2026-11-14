package com.lucas.mobilemanager.zones

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.lucas.mobilemanager.R
import com.lucas.mobilemanager.ui.theme.BackgroundDark
import com.lucas.mobilemanager.ui.theme.BackgroundLight
import com.lucas.mobilemanager.ui.theme.OnSurfaceDark
import com.lucas.mobilemanager.ui.theme.OnSurfaceLight
import com.lucas.mobilemanager.ui.theme.LavenderDark
import com.lucas.mobilemanager.ui.theme.LavenderLight
import com.lucas.mobilemanager.ui.theme.OnLavenderDark
import com.lucas.mobilemanager.ui.theme.OnLavenderLight

/** Recouvre le fil recommandé et absorbe les gestes, en laissant les barres Instagram accessibles. */
class InstagramFeedShield(
    private val service: AccessibilityService,
    private val onFollowing: () -> Unit,
) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var bounds: Rect? = null
    val isShown: Boolean get() = view != null

    fun show(rect: Rect) {
        if (rect.isEmpty) return clear()
        if (rect == bounds && view != null) return
        val params = WindowManager.LayoutParams(
            rect.width(), rect.height(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            title = "Mobile Manager Instagram feed shield"
        }
        val current = view
        if (current == null) {
            val created = createView()
            manager.addView(created, params)
            view = created
        } else {
            manager.updateViewLayout(current, params)
        }
        bounds = Rect(rect)
    }

    fun clear() {
        view?.let(manager::removeView)
        view = null
        bounds = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): View {
        val dark = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val foreground = (if (dark) OnSurfaceDark else OnSurfaceLight).toArgb()
        val container = FrameLayout(service).apply {
            setBackgroundColor((if (dark) BackgroundDark else BackgroundLight).toArgb())
            setOnTouchListener { target, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) target.performClick()
                true
            }
        }
        val content = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
        }
        fun label(resource: Int, size: Float, bold: Boolean = false) = TextView(service).apply {
            setText(resource)
            setTextColor(foreground)
            textSize = size
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(label(R.string.instagram_feed_paused, 22f, bold = true))
        content.addView(label(R.string.instagram_feed_paused_help, 16f))
        content.addView(Button(service).apply {
            setText(R.string.instagram_open_following)
            backgroundTintList = ColorStateList.valueOf((if (dark) LavenderDark else LavenderLight).toArgb())
            setTextColor((if (dark) OnLavenderDark else OnLavenderLight).toArgb())
            setOnClickListener { onFollowing() }
        })
        container.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ))
        return container
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
}
