package com.morningdigest.app.ui.history

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.data.model.DigestReport
import com.morningdigest.app.pdf.PdfExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(reportId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as MorningDigestApp).container
    var report by remember { mutableStateOf<DigestReport?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reportId) {
        report = container.digestRepository.getReportById(reportId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Detail") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    report?.let { r ->
                        IconButton(onClick = {
                            scope.launch {
                                val settings = container.settingsRepository.currentSettings()
                                val file = PdfExporter.export(context, r, settings.city)
                                val uri = PdfExporter.uriFor(context, file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share report"))
                            }
                        }) { Icon(Icons.Filled.Share, contentDescription = "Export & share PDF") }
                    }
                }
            )
        }
    ) { padding ->
        report?.let { r ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm", Locale.ENGLISH).format(Date(r.timestampMillis)),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (r.notificationSent) "✅ Notification sent" else r.notificationError?.let { "❌ Failed: $it" } ?: "Not sent",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(20.dp))

                if (r.weatherAlerts.available && r.weatherAlerts.alerts.isNotEmpty()) {
                    DetailBlock("⚠️ Severe Weather Alerts") {
                        r.weatherAlerts.alerts.forEach { alert ->
                            Text(
                                "• ${alert.event}${if (alert.senderName.isNotBlank()) " (${alert.senderName})" else ""}",
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                DetailBlock("🌤 Today's Weather") {
                    val w = r.weatherToday
                    if (w.available) {
                        Text("${w.temp?.toInt()}°C, feels like ${w.feelsLike?.toInt()}°C — ${w.description}")
                        Text("Humidity ${w.humidity}% · Wind ${w.windSpeed} m/s · Pressure ${w.pressure} hPa", style = MaterialTheme.typography.bodyMedium)
                    } else Text("Unavailable")
                }

                DetailBlock("🌦 Tomorrow") {
                    val t = r.weatherTomorrow
                    if (t.available) Text("${t.avgTemp?.toInt()}°C avg — ${t.description}, ${t.rainChancePercent}% rain chance")
                    else Text("Unavailable")
                }

                DetailBlock("₿ Bitcoin") {
                    val b = r.bitcoin
                    if (b.available) Text("€${b.eur} · \$${b.usd} · kr ${b.nok} (${b.change24hPercent}% 24h)")
                    else Text("Unavailable")
                }

                DetailBlock("💱 ${r.currency.baseCurrency} → ${r.currency.targetCurrency}") {
                    val c = r.currency
                    if (c.available) Text("1 ${c.baseCurrency} = ${c.rate} ${c.targetCurrency} (${c.change24hPercent}% 24h)")
                    else Text("Unavailable")
                }

                DetailBlock("🌍 World News") {
                    if (r.news.available) {
                        r.news.headlines.forEach { h ->
                            Text(
                                if (h.source.isNotBlank()) "• ${h.title}  —  ${h.source}" else "• ${h.title}",
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    } else Text("Unavailable")
                }
            }
        } ?: Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun DetailBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}
