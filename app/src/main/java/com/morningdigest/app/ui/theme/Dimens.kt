package com.morningdigest.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Consistent spacing/elevation scale, so every screen breathes the same way
 * instead of each card inventing its own 10.dp/12.dp/14.dp padding. Based on
 * a 4dp grid, the standard Material rhythm.
 */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    val xxl: Dp = 36.dp
}

/** Tonal elevation levels for cards - subtle, since heavy drop shadows read as dated on Material 3. */
object Elevation {
    val flat: Dp = 0.dp
    val low: Dp = 1.dp
    val medium: Dp = 3.dp
    val high: Dp = 6.dp
}
