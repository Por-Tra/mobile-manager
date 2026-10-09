package com.lucas.mobilemanager.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lucas.mobilemanager.ui.home.HomeScreen
import com.lucas.mobilemanager.ui.onboarding.OnboardingScreen
import com.lucas.mobilemanager.ui.session.OverrideScreen
import com.lucas.mobilemanager.ui.session.StartSessionScreen
import com.lucas.mobilemanager.ui.settings.SettingsScreen
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

@Composable
fun MobileManagerNavHost(startDestination: Any) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
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
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onStartSession = { navController.navigate(StartSessionRoute) },
                onRequestOverride = { navController.navigate(OverrideRoute) },
            )
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
            )
        }
    }
}
