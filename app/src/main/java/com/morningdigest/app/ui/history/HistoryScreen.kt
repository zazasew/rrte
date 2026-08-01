package com.morningdigest.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenReport: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val history by viewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear history?") },
            text = { Text("This deletes all stored reports permanently.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No reports yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { report ->
                    val dateStr = SimpleDateFormat("EEE d MMM · HH:mm", Locale.ENGLISH).format(Date(report.timestampMillis))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onOpenReport(report.id) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(dateStr, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (report.notificationSent) "✅ Notified" else if (report.notificationError != null) "❌ Failed" else "Not sent yet",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            // Delete immediately but give an easy way back: a Snackbar with
                            // Undo, instead of a blocking confirmation dialog for every tap.
                            viewModel.delete(report.id)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = "Report deleted",
                                    actionLabel = "Undo",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restore(report)
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}
