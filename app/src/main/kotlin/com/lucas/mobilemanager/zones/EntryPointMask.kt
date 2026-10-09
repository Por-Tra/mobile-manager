package com.lucas.mobilemanager.zones

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import com.lucas.mobilemanager.domain.zones.MaskColor
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Masque les points d'entrée d'une zone inaccessible (bouton Reels, onglet Shorts) : une fenêtre
 * `TYPE_ACCESSIBILITY_OVERLAY` (aucune permission supplémentaire) posée exactement sur l'élément,
 * de la couleur de la barre d'onglets, qui absorbe les appuis. L'app elle-même n'est pas modifiée.
 */
class EntryPointMask(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val views = ArrayList<View>()
    /** Laisse visible le liseré éventuel au-dessus de la barre d'onglets (YouTube). */
    private val topInset = (2 * service.resources.displayMetrics.density).toInt()
    private val shownRects = ArrayList<Pair<Rect, MaskColor>>()

    /** Affiche un masque par rectangle (coordonnées écran) ; une liste vide retire tout. */
    fun show(rects: List<Pair<Rect, MaskColor>>) {
        if (rects == shownRects) return
        while (views.size > rects.size) windowManager.removeView(views.removeAt(views.lastIndex))
        rects.forEachIndexed { i, (rect, maskColor) ->
            val color = color(maskColor)
            val params = layoutParams(rect)
            if (i < views.size) {
                views[i].setBackgroundColor(color)
                windowManager.updateViewLayout(views[i], params)
            } else {
                views += maskView().also {
                    it.setBackgroundColor(color)
                    windowManager.addView(it, params)
                }
            }
        }
        shownRects.clear()
        shownRects.addAll(rects)
    }

    fun clear() = show(emptyList())

    @SuppressLint("ClickableViewAccessibility")
    private fun maskView() = View(service).apply {
        // Absorbe l'appui : le bouton masqué ne réagit plus.
        setOnTouchListener { _, _ -> true }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Couleur de la barre d'onglets de l'app selon le thème du téléphone (que les apps suivent par défaut). */
    private fun color(maskColor: MaskColor): Int {
        val night = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return Color.parseColor(if (night) maskColor.dark else maskColor.light)
    }

    private fun layoutParams(rect: Rect) = WindowManager.LayoutParams(
        rect.width(),
        (rect.height() - topInset).coerceAtLeast(1),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = rect.left
        y = rect.top + topInset
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}
