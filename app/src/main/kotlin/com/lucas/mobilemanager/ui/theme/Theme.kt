package com.lucas.mobilemanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val LightColors = lightColorScheme(
    primary = LavenderLight,
    onPrimary = OnLavenderLight,
    primaryContainer = LavenderContainerLight,
    onPrimaryContainer = OnLavenderContainerLight,
    secondary = MintLight,
    secondaryContainer = MintContainerLight,
    tertiary = ApricotLight,
    tertiaryContainer = ApricotContainerLight,
    background = BackgroundLight,
    surface = BackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0EBFA),
    surfaceContainerHigh = Color(0xFFE9E1F7),
    surfaceContainerHighest = Color(0xFFE1D7F1),
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    onSecondaryContainer = OnSurfaceLight,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onTertiaryContainer = Color(0xFF594018),
    outline = Color(0xFF7B718C),
    outlineVariant = Color(0xFFDED6EB),
)

private val DarkColors = darkColorScheme(
    primary = LavenderDark,
    onPrimary = OnLavenderDark,
    primaryContainer = LavenderContainerDark,
    onPrimaryContainer = OnLavenderContainerDark,
    secondary = MintDark,
    secondaryContainer = MintContainerDark,
    tertiary = ApricotDark,
    tertiaryContainer = ApricotContainerDark,
    background = BackgroundDark,
    surface = BackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    surfaceContainerLow = Color(0xFF211B2D),
    surfaceContainer = Color(0xFF292135),
    surfaceContainerHigh = Color(0xFF332940),
    surfaceContainerHighest = Color(0xFF3D324C),
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    onSecondaryContainer = OnSurfaceDark,
    outline = Color(0xFF9C90AD),
    outlineVariant = Color(0xFF50445F),
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
    darkTheme: Boolean = false,
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
            typography = androidx.compose.material3.Typography(
                headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
                headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
                titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            ),
            content = content,
        )
    }
}
