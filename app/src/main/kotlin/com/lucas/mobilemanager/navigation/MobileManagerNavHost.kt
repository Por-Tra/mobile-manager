package com.lucas.mobilemanager.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lucas.mobilemanager.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lucas.mobilemanager.ui.dashboard.DashboardScreen
import com.lucas.mobilemanager.ui.diagnostic.DiagnosticScreen
import com.lucas.mobilemanager.ui.home.HomeScreen
import com.lucas.mobilemanager.ui.lock.LockSettingsScreen
import com.lucas.mobilemanager.ui.onboarding.OnboardingScreen
import com.lucas.mobilemanager.ui.profiles.ProfileEditScreen
import com.lucas.mobilemanager.ui.session.OverrideScreen
import com.lucas.mobilemanager.ui.session.StartSessionScreen
import com.lucas.mobilemanager.ui.settings.SettingsScreen
import com.lucas.mobilemanager.ui.zones.ZoneRulesScreen
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data object StartSessionRoute

@Serializable
data object OverrideRoute

@Serializable
data object ZoneRulesRoute

@Serializable
data object LockSettingsRoute

@Serializable
data object DiagnosticRoute

@Serializable
data object DashboardRoute

/** Édition d'un profil (`profileId = 0` : nouveau) ou des règles par défaut. */
@Serializable
data class ProfileEditRoute(val profileId: Long = 0, val defaultRules: Boolean = false)

@Composable
fun MobileManagerNavHost(startDestination: Any, openDiagnostic: Boolean = false) {
    val navController = rememberNavController()
    LaunchedEffect(openDiagnostic) { if (openDiagnostic) navController.navigate(DiagnosticRoute) }

    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val primary = destination?.hasRoute<HomeRoute>() == true || destination?.hasRoute<DashboardRoute>() == true || destination?.hasRoute<SettingsRoute>() == true
    fun openPrimary(route: Any) {
        navController.navigate(route) {
            popUpTo<HomeRoute> { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        if (primary) NavigationBar {
            listOf(
                Triple(HomeRoute, R.string.nav_home, Icons.Outlined.Home),
                Triple(DashboardRoute, R.string.nav_statistics, Icons.Outlined.BarChart),
                Triple(SettingsRoute, R.string.settings_title, Icons.Outlined.Settings),
            ).forEach { (route, label, icon) ->
                val selected = when (route) {
                    HomeRoute -> destination.hasRoute<HomeRoute>() == true
                    DashboardRoute -> destination.hasRoute<DashboardRoute>() == true
                    else -> destination.hasRoute<SettingsRoute>() == true
                }
                NavigationBarItem(selected = selected, onClick = { openPrimary(route) }, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
            }
        }
    }) { shellPadding ->
        NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.padding(shellPadding).consumeWindowInsets(shellPadding)) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onFinished = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(HomeRoute) {
                                popUpTo<OnboardingRoute> { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable<HomeRoute> {
                HomeScreen(
                    onOpenPermissions = { navController.navigate(OnboardingRoute) },
                    onStartSession = { navController.navigate(StartSessionRoute) },
                    onRequestOverride = { navController.navigate(OverrideRoute) },
                    onEditProfile = { navController.navigate(ProfileEditRoute(profileId = it)) },
                    onNewProfile = { navController.navigate(ProfileEditRoute()) },
                    onEditDefaultRules = { navController.navigate(ProfileEditRoute(defaultRules = true)) },
                    onOpenDashboard = { openPrimary(DashboardRoute) },
                )
            }
            composable<DashboardRoute> {
                DashboardScreen(onBack = { navController.popBackStack() })
            }
            composable<ProfileEditRoute> {
                ProfileEditScreen(onBack = { navController.popBackStack() })
            }
            composable<StartSessionRoute> {
                StartSessionScreen(
                    onBack = { navController.popBackStack() },
                    onStarted = { navController.popBackStack() },
                )
            }
            composable<OverrideRoute> {
                OverrideScreen(onBack = { navController.popBackStack() })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = { navController.navigate(OnboardingRoute) },
                    onOpenZones = { navController.navigate(ZoneRulesRoute) },
                    onOpenLock = { navController.navigate(LockSettingsRoute) },
                    onOpenDiagnostic = { navController.navigate(DiagnosticRoute) },
                )
            }
            composable<ZoneRulesRoute> {
                ZoneRulesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDefaultRules = { navController.navigate(ProfileEditRoute(defaultRules = true)) },
                )
            }
            composable<LockSettingsRoute> {
                LockSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable<DiagnosticRoute> {
                DiagnosticScreen(onBack = { navController.popBackStack() })
            }
        }
    }

}
