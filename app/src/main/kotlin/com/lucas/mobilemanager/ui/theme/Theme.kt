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
import androidx.compose.ui.unit.dp

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
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDF2EF),
    surfaceContainerHigh = Color(0xFFE6EDE9),
    surfaceContainerHighest = Color(0xFFDDE7E1),
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    onSecondaryContainer = OnSurfaceLight,
    outline = Color(0xFF6D8077),
    outlineVariant = Color(0xFFC4D0C9),
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
    surfaceContainerLow = Color(0xFF191F1D),
    surfaceContainer = Color(0xFF202825),
    surfaceContainerHigh = Color(0xFF29332F),
    surfaceContainerHighest = Color(0xFF303D36),
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    onSecondaryContainer = OnSurfaceDark,
    outline = Color(0xFF8C9F95),
    outlineVariant = Color(0xFF43534B),
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
            shapes = androidx.compose.material3.Shapes(
                small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
