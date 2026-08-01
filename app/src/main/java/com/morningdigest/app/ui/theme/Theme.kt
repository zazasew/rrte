package com.morningdigest.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Color_White = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color_White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Gold40,
    onSecondary = Color_White,
    secondaryContainer = Gold90,
    onSecondaryContainer = Gold10,
    tertiary = Teal40,
    onTertiary = Color_White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal30,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Neutral95,
    onSurfaceVariant = NeutralVariant30,
    surfaceContainerLowest = Neutral99,
    surfaceContainerLow = Neutral98,
    surfaceContainer = Neutral95,
    surfaceContainerHigh = Neutral90,
    surfaceContainerHighest = Neutral90,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    error = ErrorBase,
    onError = Color_White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF3D0D0D)
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    secondary = Gold80,
    onSecondary = Gold10,
    secondaryContainer = Gold30,
    onSecondaryContainer = Gold90,
    tertiary = Teal80,
    onTertiary = Teal30,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Neutral20,
    onSurfaceVariant = NeutralVariant80,
    surfaceContainerLowest = Color(0xFF0A0912),
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral20,
    surfaceContainerHigh = Color(0xFF2A2938),
    surfaceContainerHighest = Color(0xFF353443),
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF4E0A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainerLight
)

/**
 * Semantic colors Material 3's [androidx.compose.material3.ColorScheme] has
 * no dedicated slot for - success/warning/info - each with a container
 * variant, following the same base/onBase/container/onContainer pattern as
 * the built-in colors. Access via `MaterialTheme.extendedColors`.
 */
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

private val LightExtendedColors = ExtendedColors(
    success = SuccessBase,
    onSuccess = Color_White,
    successContainer = SuccessContainerLight,
    onSuccessContainer = Color(0xFF0B3A1F),
    warning = WarningBase,
    onWarning = Color_White,
    warningContainer = WarningContainerLight,
    onWarningContainer = Color(0xFF3D2700),
    info = InfoBase,
    onInfo = Color_White,
    infoContainer = InfoContainerLight,
    onInfoContainer = Color(0xFF0E2450)
)

private val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF8FDBA8),
    onSuccess = Color(0xFF0B3A1F),
    successContainer = SuccessContainerDark,
    onSuccessContainer = SuccessContainerLight,
    warning = Color(0xFFFFC876),
    onWarning = Color(0xFF3D2700),
    warningContainer = WarningContainerDark,
    onWarningContainer = WarningContainerLight,
    info = Color(0xFFAEC6FF),
    onInfo = Color(0xFF0E2450),
    infoContainer = InfoContainerDark,
    onInfoContainer = InfoContainerLight
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

/**
 * App-wide theme. Dynamic color (Material You, tinted from the user's
 * wallpaper) is OFF by default - this app has a deliberate brand palette
 * ("morning sky" indigo/gold/teal) and letting the system repaint it a
 * random wallpaper-derived color on every device would undercut the whole
 * point of a designed palette. Pass dynamicColor = true if that's ever
 * wanted instead.
 */
@Composable
fun MorningDigestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(androidx.compose.ui.platform.LocalContext.current)
            else androidx.compose.material3.dynamicLightColorScheme(androidx.compose.ui.platform.LocalContext.current)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
