package com.morningdigest.app.ui.mascot

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.morningdigest.app.data.prefs.MascotCharacter

/**
 * The assistant character's portrait - a square source photo (see
 * res/drawable-nodpi/mascot_*.png), masked into a circle so it reads as a
 * clean avatar regardless of what's around the edges of the source image.
 */
@Composable
fun MascotIllustration(character: MascotCharacter, modifier: Modifier = Modifier.size(120.dp)) {
    Image(
        painter = painterResource(id = character.drawableRes),
        contentDescription = "${character.displayName}, ${character.role}",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}
