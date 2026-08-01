package com.morningdigest.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.morningdigest.app.MorningDigestApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

class MorningDigestWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MorningDigestApp).container
        val report = container.digestRepository.getLatestReport()
        val settings = container.settingsRepository.currentSettings()

        provideContent {
            Column(
                modifier = androidx.glance.GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1B2E))
                    .padding(12.dp)
            ) {
                // Header: city + today's temp/description
                Text(
                    "🌅 ${settings.city}",
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
                if (report != null && report.weatherToday.available) {
                    val w = report.weatherToday
                    Row(modifier = androidx.glance.GlanceModifier.fillMaxWidth()) {
                        Text(
                            "${w.temp?.roundToInt() ?: "—"}°C",
                            style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        )
                        Spacer(modifier = androidx.glance.GlanceModifier.padding(horizontal = 6.dp))
                        Text(
                            w.description?.replaceFirstChar { it.uppercase() } ?: "",
                            style = TextStyle(color = ColorProvider(Color(0xFFB8B4D6)), fontSize = 12.sp)
                        )
                    }
                } else {
                    Text("—°C", style = TextStyle(color = ColorProvider(Color.White)))
                }

                Spacer(modifier = androidx.glance.GlanceModifier.height(6.dp))

                // Tomorrow's forecast
                val tomorrow = report?.weatherTomorrow
                Text(
                    if (tomorrow?.available == true)
                        "Tomorrow: ${tomorrow.avgTemp?.roundToInt() ?: "—"}° avg · ☔ ${tomorrow.rainChancePercent ?: 0}%"
                    else "Tomorrow: —",
                    style = TextStyle(color = ColorProvider(Color(0xFFB8B4D6)), fontSize = 11.sp)
                )

                Spacer(modifier = androidx.glance.GlanceModifier.height(8.dp))

                // Bitcoin, with 24h change
                val bitcoin = report?.bitcoin
                Row(modifier = androidx.glance.GlanceModifier.fillMaxWidth()) {
                    Text(
                        if (bitcoin?.available == true) "₿ €${bitcoin.eur?.toInt()}" else "₿ —",
                        style = TextStyle(color = ColorProvider(Color(0xFFFFC44D)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    if (bitcoin?.available == true && bitcoin.change24hPercent != null) {
                        Spacer(modifier = androidx.glance.GlanceModifier.padding(horizontal = 6.dp))
                        val change = bitcoin.change24hPercent
                        Text(
                            "${if (change >= 0) "▲" else "▼"} ${"%.2f".format(abs(change))}%",
                            style = TextStyle(
                                color = ColorProvider(if (change >= 0) Color(0xFF4ADE80) else Color(0xFFFF6B6B)),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Configured currency pair, with 24h change
                val currency = report?.currency
                Row(modifier = androidx.glance.GlanceModifier.fillMaxWidth()) {
                    Text(
                        if (currency?.available == true) "${currency.baseCurrency}→${currency.targetCurrency} ${String.format(Locale.US, "%.2f", currency.rate)}" else "Currency —",
                        style = TextStyle(color = ColorProvider(Color(0xFFB8B4D6)), fontSize = 11.sp)
                    )
                    if (currency?.available == true && currency.change24hPercent != null) {
                        Spacer(modifier = androidx.glance.GlanceModifier.padding(horizontal = 6.dp))
                        val change = currency.change24hPercent
                        Text(
                            "${if (change >= 0) "▲" else "▼"} ${"%.2f".format(abs(change))}%",
                            style = TextStyle(
                                color = ColorProvider(if (change >= 0) Color(0xFF4ADE80) else Color(0xFFFF6B6B)),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Top headline
                val headline = report?.news?.headlines?.firstOrNull()
                if (headline != null) {
                    Spacer(modifier = androidx.glance.GlanceModifier.height(8.dp))
                    Text(
                        "📰 ${headline.title}",
                        maxLines = 2,
                        style = TextStyle(color = ColorProvider(Color(0xFFE4E1F5)), fontSize = 11.sp)
                    )
                }

                report?.let {
                    Spacer(modifier = androidx.glance.GlanceModifier.height(6.dp))
                    val lastUpdate = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestampMillis))
                    Text(
                        "Updated $lastUpdate",
                        style = TextStyle(color = ColorProvider(Color(0xFF8A85A8)), fontSize = 10.sp)
                    )
                }
            }
        }
    }
}

class MorningDigestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MorningDigestWidget()
}
