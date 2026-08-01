package com.morningdigest.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Full Material 3 type scale, tuned for a weather/dashboard app: slightly
 * tighter line-heights than stock Material for a denser, more "native
 * Pixel app" feel, and bold weights reserved for headlines/titles/numbers so
 * emphasis actually reads as emphasis instead of everything being semi-bold.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),

    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),

    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),

    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp)
)

/**
 * Extra display styles beyond the standard M3 scale, for the big weather
 * numbers (current temperature, etc.) - not part of [Typography] since M3's
 * Typography type doesn't have a slot big/tabular enough for a hero number.
 */
object NumericStyles {
    val heroValue = TextStyle(fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1).sp)
    val largeValue = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.25).sp)
    val mediumValue = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
}
