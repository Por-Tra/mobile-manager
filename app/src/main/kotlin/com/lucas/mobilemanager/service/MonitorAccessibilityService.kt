package com.lucas.mobilemanager.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Source de vérité de l'app au premier plan.
 * Phase 1 : déclaré pour pouvoir être activé depuis l'onboarding ; la logique arrive en phase 3.
 */
class MonitorAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
