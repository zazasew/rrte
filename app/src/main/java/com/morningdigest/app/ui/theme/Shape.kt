package com.morningdigest.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One consistent corner-radius scale for the whole app, instead of every
 * screen picking its own RoundedCornerShape(14.dp)/(16.dp)/(20.dp) ad hoc.
 * Small = chips/badges, Medium = list rows/inputs, Large = cards (the most
 * common shape in this app), ExtraLarge = dialogs/sheets/hero cards.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
