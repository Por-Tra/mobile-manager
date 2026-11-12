package com.lucas.mobilemanager.diagnostic

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Connexion effective dans ce processus, distincte de l'autorisation d'accessibilité. */
@Singleton
class MonitorHealth @Inject constructor() {
    val connected = MutableStateFlow(false)
}
