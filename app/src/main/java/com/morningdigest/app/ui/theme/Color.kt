package com.morningdigest.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's full semantic color system. Every screen should pull colors from
 * here (or from MaterialTheme.colorScheme, which these feed into) rather than
 * hardcoding hex values inline - that inconsistency was the biggest visual
 * smell in the old screens (a different ad hoc pastel background per card).
 *
 * Brand: a deep "morning sky" indigo as primary, warm sunrise gold as
 * secondary/accent, and a calm teal as tertiary - plus dedicated, distinct
 * warning/success/info colors so status colors never get confused with the
 * brand palette.
 */

// ---------- Brand ----------
val Indigo10 = Color(0xFF0F0B3D)
val Indigo20 = Color(0xFF211A5E)
val Indigo30 = Color(0xFF322888)
val Indigo40 = Color(0xFF4A3FCF)
val Indigo80 = Color(0xFFC6BFFF)
val Indigo90 = Color(0xFFE7E2FF)
val Indigo95 = Color(0xFFF3F0FF)

val Gold10 = Color(0xFF2B1A00)
val Gold30 = Color(0xFF6B4A00)
val Gold40 = Color(0xFFB8860B)
val Gold80 = Color(0xFFFFDD9E)
val Gold90 = Color(0xFFFFEBC7)

val Teal30 = Color(0xFF00504A)
val Teal40 = Color(0xFF00695F)
val Teal80 = Color(0xFF80D8CE)
val Teal90 = Color(0xFFB3F0E8)

// ---------- Neutral surfaces ----------
val Neutral0 = Color(0xFF000000)
val Neutral10 = Color(0xFF141320)
val Neutral20 = Color(0xFF201F2E)
val Neutral90 = Color(0xFFE7E5F0)
val Neutral95 = Color(0xFFF4F3FA)
val Neutral98 = Color(0xFFFBFAFF)
val Neutral99 = Color(0xFFFFFFFF)

val NeutralVariant30 = Color(0xFF454559)
val NeutralVariant50 = Color(0xFF767689)
val NeutralVariant60 = Color(0xFF908FA3)
val NeutralVariant80 = Color(0xFFC9C7DA)
val NeutralVariant90 = Color(0xFFE5E2F5)

// ---------- Status (kept distinct from brand so meaning is never ambiguous) ----------
val SuccessBase = Color(0xFF1F9D55)
val SuccessContainerLight = Color(0xFFDCF5E4)
val SuccessContainerDark = Color(0xFF0E3B22)

val WarningBase = Color(0xFFB36B00)
val WarningContainerLight = Color(0xFFFFEBCF)
val WarningContainerDark = Color(0xFF4A3000)

val ErrorBase = Color(0xFFD64545)
val ErrorContainerLight = Color(0xFFFDE2E2)
val ErrorContainerDark = Color(0xFF4E1616)

val InfoBase = Color(0xFF2F6FED)
val InfoContainerLight = Color(0xFFDCE8FF)
val InfoContainerDark = Color(0xFF10264F)

// ---------- Legacy aliases (kept so any remaining old references still compile) ----------
val IndigoPrimary = Indigo40
val IndigoPrimaryLight = Indigo80
val IndigoPrimaryDark = Indigo20
val SunAccent = Gold40
val CoralAccent = Color(0xFFFF6F61)
val MintSuccess = SuccessBase
val DangerRed = ErrorBase

val BackgroundLight = Neutral98
val SurfaceLight = Neutral99
val OnSurfaceLight = Neutral10
val MutedLight = NeutralVariant50

val BackgroundDark = Color(0xFF0D0C16)
val SurfaceDark = Neutral10
val OnSurfaceDark = Neutral90
val MutedDark = NeutralVariant60
