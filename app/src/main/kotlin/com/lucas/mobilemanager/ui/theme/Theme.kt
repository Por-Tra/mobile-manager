package com.lucas.mobilemanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = SageLight,
    onPrimary = OnSageLight,
    primaryContainer = SageContainerLight,
    onPrimaryContainer = OnSageContainerLight,
    secondary = SlateLight,
    secondaryContainer = SlateContainerLight,
    tertiary = DuskLight,
    tertiaryContainer = DuskContainerLight,
    background = BackgroundLight,
    surface = BackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
)

private val DarkColors = darkColorScheme(
    primary = SageDark,
    onPrimary = OnSageDark,
    primaryContainer = SageContainerDark,
    onPrimaryContainer = OnSageContainerDark,
    secondary = SlateDark,
    secondaryContainer = SlateContainerDark,
    tertiary = DuskDark,
    tertiaryContainer = DuskContainerDark,
    background = BackgroundDark,
    surface = BackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
)

/** Couleurs propres à l'app, hors schéma Material. */
@Immutable
data class ExtendedColors(
    /** Fond neutre pour « limite atteinte » : informatif, jamais alarmant. */
    val limitReachedContainer: Color,
    val onLimitReachedContainer: Color,
)

private val LightExtended = ExtendedColors(
    limitReachedContainer = LimitReachedContainerLight,
    onLimitReachedContainer = OnLimitReachedContainerLight,
)

private val DarkExtended = ExtendedColors(
    limitReachedContainer = LimitReachedContainerDark,
    onLimitReachedContainer = OnLimitReachedContainerDark,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

@Composable
fun MobileManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
