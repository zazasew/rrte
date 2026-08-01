package com.morningdigest.app.ui.mascot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morningdigest.app.data.prefs.MascotCharacter
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos

/**
 * Wraps [MascotIllustration] with a small always-on animation so the
 * Assistants tab feels alive instead of a static list of photos, and gives
 * each character its own tiny animated badge tied to their role:
 * - Bully (bull) gets a pulsing 👍, Beary (bear) a pulsing 👎
 * - Scoop (world news) gets a small spinning globe 🌐
 * - Satoshi (crypto) gets a slow spinning ₿
 * - Panda (business) gets a wiggling necktie 👔
 * - Anja (politics) gets a pulsing 🏛️
 * - Max (police) gets a spinning police shield 🛡️
 *
 * Deliberately NOT built on [androidx.compose.animation.core.rememberInfiniteTransition]:
 * that API intentionally freezes to a static frame when the device's
 * animator duration scale is 0 - which happens whenever the "Remove
 * animations" accessibility setting is on, an option that's more commonly
 * surfaced on Android 14. That's almost certainly why these went static on
 * that phone. Driving the values by hand with a plain per-frame loop below
 * ignores that setting so the mascots always animate, since this motion is
 * purely decorative rather than something that could cause discomfort.
 */
@Composable
fun AnimatedMascotIllustration(
    character: MascotCharacter,
    modifier: Modifier = Modifier.size(120.dp),
    size: Dp = 120.dp,
    seedIndex: Int = 0
) {
    var floatPx by remember { mutableFloatStateOf(0f) }
    var wobbleDeg by remember { mutableFloatStateOf(0f) }
    var badgeScale by remember { mutableFloatStateOf(1f) }
    var fastSpinDeg by remember { mutableFloatStateOf(0f) }
    var slowSpinDeg by remember { mutableFloatStateOf(0f) }
    var tieWiggleDeg by remember { mutableFloatStateOf(0f) }
    var shieldSpinDeg by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(character, seedIndex) {
        val floatPeriodMs = 1500f + seedIndex * 130f
        val wobblePeriodMs = 2100f + seedIndex * 170f
        val badgePeriodMs = 750f
        val fastSpinPeriodMs = 2600f + seedIndex * 90f   // globe
        val slowSpinPeriodMs = 4800f + seedIndex * 140f  // bitcoin
        val tiePeriodMs = 1100f
        val shieldSpinPeriodMs = 3400f + seedIndex * 110f // police shield

        var startNanos = -1L
        while (isActive) {
            withFrameNanos { nowNanos ->
                if (startNanos < 0L) startNanos = nowNanos
                val elapsedMs = (nowNanos - startNanos) / 1_000_000f

                floatPx = oscillate(elapsedMs, floatPeriodMs, -6f, 6f)
                wobbleDeg = oscillate(elapsedMs, wobblePeriodMs, -5f, 5f)
                badgeScale = oscillate(elapsedMs, badgePeriodMs, 0.85f, 1.2f)
                tieWiggleDeg = oscillate(elapsedMs, tiePeriodMs, -10f, 10f)
                fastSpinDeg = (elapsedMs % fastSpinPeriodMs) / fastSpinPeriodMs * 360f
                slowSpinDeg = (elapsedMs % slowSpinPeriodMs) / slowSpinPeriodMs * 360f
                shieldSpinDeg = (elapsedMs % shieldSpinPeriodMs) / shieldSpinPeriodMs * 360f
            }
        }
    }

    Box(contentAlignment = Alignment.Center) {
        MascotIllustration(
            character,
            modifier = modifier.graphicsLayer {
                translationY = floatPx
                rotationZ = wobbleDeg
            }
        )

        when (character) {
            MascotCharacter.BULL -> MascotBadge("👍", size, floatPx) { scaleX = badgeScale; scaleY = badgeScale }
            MascotCharacter.BEAR -> MascotBadge("👎", size, floatPx) { scaleX = badgeScale; scaleY = badgeScale }
            MascotCharacter.OWL -> MascotBadge("🌐", size, floatPx) { rotationZ = fastSpinDeg }
            MascotCharacter.FOX -> MascotBadge("₿", size, floatPx) { rotationZ = slowSpinDeg }
            MascotCharacter.PANDA -> MascotBadge("👔", size, floatPx) { rotationZ = tieWiggleDeg }
            MascotCharacter.CAT -> MascotBadge("🏛️", size, floatPx) { scaleX = badgeScale; scaleY = badgeScale }
            MascotCharacter.MAX -> MascotBadge("🛡️", size, floatPx) { rotationZ = shieldSpinDeg }
        }
    }
}

/** Smooth, seamless min<->max oscillation with no snap at the loop point (unlike a sawtooth/triangle wave). */
private fun oscillate(elapsedMs: Float, periodMs: Float, min: Float, max: Float): Float {
    val phase = (elapsedMs % periodMs) / periodMs
    val eased = (1f - cos(2f * PI.toFloat() * phase)) / 2f
    return min + (max - min) * eased
}

@Composable
private fun BoxScope.MascotBadge(
    emoji: String,
    size: Dp,
    floatPx: Float,
    transform: GraphicsLayerScope.() -> Unit
) {
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .graphicsLayer {
                translationY = floatPx
                transform()
            }
    ) {
        Text(emoji, fontSize = (size.value * 0.34f).sp, style = MaterialTheme.typography.bodyLarge)
    }
}
