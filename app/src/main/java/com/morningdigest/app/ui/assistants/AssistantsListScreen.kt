package com.morningdigest.app.ui.assistants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.data.prefs.MascotCharacter
import com.morningdigest.app.ui.mascot.AnimatedMascotIllustration

/**
 * A dedicated screen (not a popup) listing the analysts the user has turned
 * on in Settings, each as a colorful card themed around that character's own
 * accent - avatar in a tinted ring, name/role, one-line description of what
 * they cover. Tapping a card opens [AssistantDetailScreen] for that
 * character. Purely a menu - no chat, no interaction beyond navigating in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantsListScreen(onBack: () -> Unit, onOpenCharacter: (String) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as MorningDigestApp).container
    var enabledIds by remember { mutableStateOf(MascotCharacter.ALL_IDS) }

    LaunchedEffect(Unit) {
        enabledIds = container.settingsRepository.currentSettings().enabledAssistantIds
    }

    val visible = MascotCharacter.entries.filter { it.id in enabledIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistants") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (visible.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
            ) {
                Text(
                    "No assistants are turned on. Head to Settings > Assistant Reports to enable the ones you want.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        "Your daily analysts",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Each one covers their own topic - tap a card for the full briefing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            itemsIndexed(visible) { index, character ->
                val accent = Color(character.accentColorArgb)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent.copy(alpha = 0.20f), MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        )
                        .clickable { onOpenCharacter(character.id) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedMascotIllustration(character, modifier = Modifier.size(46.dp), size = 46.dp, seedIndex = index)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            character.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = accent
                        )
                        Spacer(Modifier.height(1.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(character.role, style = MaterialTheme.typography.labelSmall, color = accent)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            character.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = accent)
                }
            }
        }
    }
}
