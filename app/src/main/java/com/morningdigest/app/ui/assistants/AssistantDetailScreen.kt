package com.morningdigest.app.ui.assistants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.data.facts.AssistantDetailLine
import com.morningdigest.app.data.facts.AssistantReportBuilder
import com.morningdigest.app.data.model.DigestReport
import com.morningdigest.app.data.prefs.MascotCharacter
import com.morningdigest.app.data.remote.PoliceReportFetcher
import com.morningdigest.app.ui.mascot.AnimatedMascotIllustration
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The dedicated per-character report screen - a fuller version of that
 * analyst's briefing (multiple headlines/movers, not just one line), with
 * its own refresh button. Still no chat, no interaction beyond refresh.
 *
 * Max (police) is special-cased throughout: his "report" is the live public
 * police incident feed rather than a DigestReport-derived summary, so this
 * screen loads/refreshes it directly from [PoliceReportFetcher] and renders
 * a dedicated list (up to 20 incidents, newest first, full text + time/date)
 * instead of the generic [AssistantDetailLine] rows the other analysts use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantDetailScreen(characterId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as MorningDigestApp).container
    val character = MascotCharacter.fromId(characterId) ?: MascotCharacter.PANDA
    val isPolice = character == MascotCharacter.MAX

    var report by remember { mutableStateOf<DigestReport?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Max-only state: his own live incident list, the configured municipality
    // (for the "<Municipality> Police Report" title), and when it was last fetched.
    var policeIncidents by remember { mutableStateOf<List<PoliceReportFetcher.Incident>>(emptyList()) }
    var municipalities by remember { mutableStateOf<List<String>>(emptyList()) }
    var policeUpdatedMillis by remember { mutableStateOf<Long?>(null) }
    var policeError by remember { mutableStateOf<String?>(null) }
    // Which municipality sections are expanded - absent/true means expanded,
    // so a newly-seen municipality (or the very first load) starts open.
    var expandedMunicipalities by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    // The thread currently open in the full-screen detail view (null = list view).
    var openThread by remember { mutableStateOf<List<PoliceReportFetcher.Incident>?>(null) }

    suspend fun loadPoliceIncidents() {
        val settings = container.settingsRepository.currentSettings()
        municipalities = settings.policeMunicipalities.ifEmpty { listOf(settings.policeMunicipality) }
        val dismissed = container.settingsRepository.getPoliceDismissedIds()
        policeError = null
        var timedOut = false
        policeIncidents = runCatching {
            val fetched = withTimeoutOrNull(30_000L) {
                container.policeReportFetcher.fetch(
                    municipalities,
                    settings.policeCategories
                )
            }
            timedOut = fetched == null
            // No extra global cap here: fetch() already guarantees up to
            // PER_MUNICIPALITY_LIMIT per municipality, fairly. Trimming
            // again to one flat top-N here would undo that fairness (a
            // busy municipality could still crowd out a quiet one at this
            // display step even though the fetch itself was fair).
            fetched.orEmpty()
                .filter { it.id !in dismissed }
                .sortedByDescending { it.createdMillis }
        }.onFailure { e ->
            policeError = (e as? PoliceReportFetcher.PoliceReportException)?.message
                ?: "Couldn't refresh the police report"
        }.getOrDefault(emptyList())
        if (policeError == null && timedOut) {
            policeError = "Timed out reaching the police report service"
        }
        // A failed live fetch can silently fall back to an older cached
        // report (see PoliceReportFetcher's cache) - use the cache's real
        // saved time here rather than "now", so the screen never implies a
        // stale report is fresh.
        val cacheAge = container.policeReportFetcher.cacheAgeMillis()
        policeUpdatedMillis = cacheAge?.let { System.currentTimeMillis() - it } ?: System.currentTimeMillis()
    }

    LaunchedEffect(characterId) {
        if (isPolice) loadPoliceIncidents() else report = container.digestRepository.getLatestReport()
    }

    fun refresh() {
        scope.launch {
            isRefreshing = true
            if (isPolice) {
                runCatching { loadPoliceIncidents() }
            } else {
                runCatching {
                    val settings = container.settingsRepository.currentSettings()
                    when (character) {
                        MascotCharacter.PANDA -> container.digestRepository.refreshBusinessSection(settings)
                        MascotCharacter.OWL -> container.digestRepository.refreshWorldNewsSection(settings)
                        MascotCharacter.BULL, MascotCharacter.BEAR, MascotCharacter.FOX ->
                            container.digestRepository.refreshMarketsSection(settings)
                        MascotCharacter.CAT -> container.digestRepository.refreshPoliticsSection(settings)
                        MascotCharacter.MAX -> null // unreachable, isPolice branch handles Max
                    }
                }.getOrNull()?.let { report = it }
            }
            isRefreshing = false
        }
    }

    val accent = Color(character.accentColorArgb)
    val lines = remember(report, character) {
        if (isPolice) emptyList() else AssistantReportBuilder.buildDetailLines(character, report)
    }
    val uriHandler = LocalUriHandler.current
    val headerTitle = if (isPolice) "Police Reports" else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(character.displayName, style = MaterialTheme.typography.titleMedium, color = accent)
                        Text(character.role, style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                    } else {
                        IconButton(onClick = { refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedMascotIllustration(character, modifier = Modifier.size(60.dp), size = 60.dp)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column {
                        if (isPolice) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛡️", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.size(6.dp))
                                Text(headerTitle ?: "Police Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                            }
                            Spacer(Modifier.size(4.dp))
                            Text(character.description, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(character.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        val updatedMillis = if (isPolice) policeUpdatedMillis else report?.timestampMillis
                        updatedMillis?.let {
                            Spacer(Modifier.size(6.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "Updated ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                        }
                    }
                }
            }
            if (isPolice) {
                if (policeError != null) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Couldn't load the police report: $policeError",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { scope.launch { loadPoliceIncidents() } }) {
                                Text("Try again")
                            }
                        }
                    }
                } else if (policeIncidents.isEmpty()) {
                    item {
                        Text(
                            if (municipalities.isNotEmpty()) "No public police incidents right now for ${municipalities.joinToString(", ")}."
                            else "No public police incidents right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                val groupedByMunicipality = policeIncidents
                    .groupBy { it.municipality.ifBlank { "Unknown municipality" } }
                    .toList()
                    .sortedBy { entry -> entry.first.lowercase(Locale.ENGLISH) }

                groupedByMunicipality.forEachIndexed { index, (municipalityName, incidentsInMunicipality) ->
                    // Group into threads (one report can have several updates
                    // over time - initial report, then follow-ups) so each
                    // report shows its full chronological history in one
                    // card, the same way the real Politiloggen page does,
                    // instead of every update appearing as its own separate
                    // entry in the list.
                    val threads = incidentsInMunicipality
                        .groupBy { it.threadId }
                        .values
                        .map { it.sortedBy { msg -> msg.createdMillis } }
                        .sortedByDescending { thread -> thread.last().createdMillis }

                    item(key = "municipality_header_$municipalityName") {
                        val expanded = expandedMunicipalities[municipalityName] ?: false
                        PoliceMunicipalityHeader(
                            municipalityName = municipalityName,
                            count = threads.size,
                            color = municipalityHeaderColors[index % municipalityHeaderColors.size],
                            expanded = expanded,
                            onToggle = {
                                expandedMunicipalities = expandedMunicipalities.toMutableMap().apply {
                                    put(municipalityName, !expanded)
                                }
                            }
                        )
                    }
                    if (expandedMunicipalities[municipalityName] ?: false) {
                        items(
                            items = threads,
                            key = { thread -> thread.first().threadId }
                        ) { thread ->
                            PoliceThreadCard(thread, accent, onClick = { openThread = thread })
                        }
                    }
                }
                if (policeIncidents.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri("https://www.politiet.no/politiloggen") } },
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("Source: Norwegian Police · Politiloggen", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                items(lines) { line -> AssistantDetailLineRow(line, uriHandler) }
            }
        }
    }

    openThread?.let { thread ->
        PoliceThreadDetailDialog(thread = thread, accent = accent, uriHandler = uriHandler, onDismiss = { openThread = null })
    }
}

@Composable
private fun AssistantDetailLineRow(line: AssistantDetailLine, uriHandler: androidx.compose.ui.platform.UriHandler) {
    val isLink = line.link != null
    val moveTint = when {
        line.text.contains("▲") -> Color(0xFF2ECC71)
        line.text.contains("▼") -> Color(0xFFE53935)
        else -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (isLink) Modifier.clickable {
                    runCatching { uriHandler.openUri(line.link!!) }
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            line.text,
            style = MaterialTheme.typography.bodyMedium,
            color = moveTint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isLink) {
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = "Open article",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One row in Max's full report - unlike the other analysts' single-line
 * rows, this shows the full (untruncated) translated text, the category and
 * area, and a formatted date + time, plus a link out to the official source.
 */
private val municipalityHeaderColors = listOf(
    Color(0xFF4FC3F7),
    Color(0xFFFFB74D),
    Color(0xFF81C784),
    Color(0xFFBA68C8),
    Color(0xFFE57373),
    Color(0xFF4DB6AC)
)

@Composable
private fun PoliceMunicipalityHeader(
    municipalityName: String,
    count: Int,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(top = 10.dp, bottom = 4.dp, start = 2.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 5.dp, height = 24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "$municipalityName Police Reports",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse $municipalityName reports" else "Expand $municipalityName reports",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One report in Max's list, collapsed to a single short line - "Fire at
 * Storgata", "Traffic incident on E6", etc. - so the list reads like a
 * headline feed instead of a wall of text. Tapping opens the full
 * chronological thread (initial report + every follow-up, all translated)
 * in [PoliceThreadDetailDialog].
 */
@Composable
private fun PoliceThreadCard(
    thread: List<PoliceReportFetcher.Incident>,
    accent: Color,
    onClick: () -> Unit
) {
    val latest = thread.last()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                threadHeadline(thread),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                "${formatFullIncidentTime(latest.createdMillis)}${if (thread.size > 1) " · ${thread.size} updates" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(8.dp))
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = "Open full report",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The full chronological thread in its own screen ("new window") - initial
 * report first, then every follow-up in order, each fully translated to
 * English. A link at the bottom leads to the real Politiloggen page (its
 * only version, since the site itself has no English toggle - see
 * [PoliceReportFetcher.sourceUrl]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoliceThreadDetailDialog(
    thread: List<PoliceReportFetcher.Incident>,
    accent: Color,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onDismiss: () -> Unit
) {
    val latest = thread.last()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            threadHeadline(thread),
                            style = MaterialTheme.typography.titleMedium,
                            color = accent
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        "${latest.categoryEn} · ${latest.municipality}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(thread, key = { it.id }) { message ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            formatFullIncidentTime(message.createdMillis),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(message.englishText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    Spacer(Modifier.size(4.dp))
                    TextButton(
                        onClick = { runCatching { uriHandler.openUri(latest.sourceUrl) } },
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("View original report on Politiloggen (Norwegian)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * A short headline for a report - just the type of incident and where, e.g.
 * "Fire at Storgata", "Crash on E6", "Missing person near Lillehammer" -
 * nothing pulled from the report's own wording, so it stays short and
 * predictable no matter how a given translation reads.
 */
private fun threadHeadline(thread: List<PoliceReportFetcher.Incident>): String {
    val latest = thread.last()
    val place = latest.area.ifBlank { latest.municipality }.ifBlank { null }
    val preposition = when (latest.category) {
        "Brann" -> "Fire at"
        "Trafikk" -> "Crash on"
        "Ulykke" -> "Accident at"
        "Tyveri" -> "Theft at"
        "Innbrudd" -> "Burglary at"
        "Voldshendelse" -> "Violence at"
        "Skadeverk" -> "Vandalism at"
        "Ro og orden" -> "Disturbance at"
        "Savnet" -> "Missing person near"
        "Dyr" -> "Animal incident at"
        "Sjø" -> "Maritime incident near"
        "Vær" -> "Weather incident at"
        "Arrangement" -> "Event at"
        else -> "${latest.categoryEn} at"
    }
    return if (place != null) "$preposition $place" else "$preposition (location not given)"
}

private fun formatFullIncidentTime(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.ENGLISH).format(Date(millis))
