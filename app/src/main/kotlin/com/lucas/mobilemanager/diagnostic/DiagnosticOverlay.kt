package com.lucas.mobilemanager.diagnostic

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lucas.mobilemanager.R

/**
 * Bandeau du mode diagnostic, affiché par le service d'accessibilité
 * (`TYPE_ACCESSIBILITY_OVERLAY` : aucune permission supplémentaire). Déplaçable au doigt.
 * Vues Android classiques : Compose demanderait un cycle de vie qu'un service n'a pas.
 */
class DiagnosticOverlay(
    private val service: AccessibilityService,
    private val onCapture: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    private val status = TextView(service).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        text = service.getString(R.string.diagnostic_overlay_waiting)
    }

    private val root = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(6), dp(6), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.argb(220, 30, 50, 46))
        }
        addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(service).apply {
            text = service.getString(R.string.diagnostic_capture)
            textSize = 12f
            setOnClickListener { onCapture() }
        })
    }

    private val params = WindowManager.LayoutParams(
        dp(300),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(16)
        y = dp(120)
    }

    private var shown = false

    init {
        enableDrag()
    }

    fun show() {
        if (shown) return
        windowManager.addView(root, params)
        shown = true
    }

    fun hide() {
        if (!shown) return
        windowManager.removeView(root)
        shown = false
    }

    fun update(text: String) {
        status.text = text
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag() {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        status.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    if (shown) windowManager.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }
    }
}
