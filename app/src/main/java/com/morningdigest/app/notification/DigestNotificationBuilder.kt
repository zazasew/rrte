package com.morningdigest.app.notification

import com.morningdigest.app.data.model.DigestReport
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Builds the plain-text "morning brief" shown inside the digest notification
 * (title + short summary line + expanded BigText body). Notifications can't
 * render HTML, so this is deliberately simple, scannable text instead of the
 * old email's HTML layout.
 */
object DigestNotificationBuilder {

    data class GreetingStyle(val emoji: String, val headline: String)

    private fun greetingStyleFor(hour: Int): GreetingStyle = when (hour) {
        in 5..11 -> GreetingStyle("🌅", "Good Morning")
        in 12..17 -> GreetingStyle("☀️", "Good Afternoon")
        in 18..21 -> GreetingStyle("🌇", "Good Evening")
        else -> GreetingStyle("🌙", "Good Evening")
    }

    fun title(report: DigestReport, userName: String): String {
        val hour = Calendar.getInstance().apply { timeInMillis = report.timestampMillis }.get(Calendar.HOUR_OF_DAY)
        val style = greetingStyleFor(hour)
        val name = userName.ifBlank { "there" }
        return "${style.emoji} ${style.headline}, $name"
    }

    /** One-line collapsed summary — shown when the notification isn't expanded. */
    fun shortSummary(report: DigestReport): String {
        val parts = mutableListOf<String>()
        val w = report.weatherToday
        if (w.available) parts.add("${w.temp?.roundToInt() ?: "—"}° ${w.description?.replaceFirstChar { it.uppercase() } ?: ""}".trim())
        val b = report.bitcoin
        if (b.available) parts.add("₿ €${"%,.0f".format(b.eur ?: 0.0)}")
        val c = report.currency
        if (c.available) parts.add("1${c.baseCurrency}=${"%.2f".format(c.rate ?: 0.0)}${c.targetCurrency}")
        if (report.news.available) parts.add("${report.news.headlines.size} news")
        if (report.weatherAlerts.available && report.weatherAlerts.alerts.isNotEmpty()) {
            parts.add(0, "⚠️ ${report.weatherAlerts.alerts.size} alert${if (report.weatherAlerts.alerts.size == 1) "" else "s"}")
        }
        return if (parts.isEmpty()) "Your morning brief is ready" else parts.joinToString("  ·  ")
    }

    /**
     * A handful of short bullet lines summarizing the whole day's digest -
     * powers the "Summary" button on the dashboard, which shows this in a
     * small popup instead of making the user scroll through every card.
     */
    fun daySummaryLines(report: DigestReport): List<String> {
        val lines = mutableListOf<String>()

        if (report.weatherAlerts.available && report.weatherAlerts.alerts.isNotEmpty()) {
            lines.add("⚠️ ${report.weatherAlerts.alerts.size} severe weather alert${if (report.weatherAlerts.alerts.size == 1) "" else "s"} active")
        }

        val w = report.weatherToday
        lines.add(
            if (w.available) "🌤 Now: ${w.temp?.roundToInt() ?: "—"}°, ${w.description ?: "—"}"
            else "🌤 Weather unavailable"
        )

        val t = report.weatherTomorrow
        if (t.available) {
            lines.add("🌦 Tomorrow: ${t.avgTemp?.roundToInt() ?: "—"}° avg, ☔ ${t.rainChancePercent ?: 0}% rain")
        }

        val b = report.bitcoin
        if (b.available) {
            val change = b.change24hPercent ?: 0.0
            lines.add("₿ Bitcoin: €${"%,.0f".format(b.eur ?: 0.0)} (${if (change >= 0) "▲" else "▼"} ${"%.2f".format(kotlin.math.abs(change))}%)")
        }

        val c = report.currency
        if (c.available) {
            lines.add("💱 1 ${c.baseCurrency} = ${"%.4f".format(c.rate ?: 0.0)} ${c.targetCurrency}")
        }

        if (report.news.available && report.news.headlines.isNotEmpty()) {
            val top = report.news.headlines.take(4)
            lines.add("🌍 ${report.news.headlines.size} news stories today:")
            top.forEach { h ->
                val src = if (h.source.isNotBlank()) " (${h.source})" else ""
                lines.add("• ${h.title}$src")
            }
            val remaining = report.news.headlines.size - top.size
            if (remaining > 0) {
                lines.add("…and $remaining more in the News card")
            }
        }

        return lines
    }

    /** Full expanded body (BigTextStyle) — weather, tomorrow, markets, and top headlines. */
    fun fullBrief(report: DigestReport, cityLabel: String): String {
        val sb = StringBuilder()
        val dateStr = SimpleDateFormat("EEEE, d MMMM · HH:mm", Locale.ENGLISH).format(Date(report.timestampMillis))
        sb.append("📍 $cityLabel — $dateStr\n\n")

        if (report.weatherAlerts.available && report.weatherAlerts.alerts.isNotEmpty()) {
            sb.append("⚠️ SEVERE WEATHER ALERT\n")
            report.weatherAlerts.alerts.take(3).forEach { alert ->
                sb.append("   ${alert.event}${if (alert.senderName.isNotBlank()) " (${alert.senderName})" else ""}\n")
            }
            sb.append("\n")
        }

        val w = report.weatherToday
        if (w.available) {
            sb.append("🌤 Now: ${w.temp?.roundToInt() ?: "—"}° (feels ${w.feelsLike?.roundToInt() ?: "—"}°), ${w.description ?: ""}\n")
            sb.append("   Humidity ${w.humidity ?: "—"}% · Wind ${w.windSpeed ?: "—"} m/s\n")
        } else {
            sb.append("🌤 Weather unavailable\n")
        }

        val t = report.weatherTomorrow
        if (t.available) {
            sb.append("🌦 Tomorrow: ${t.avgTemp?.roundToInt() ?: "—"}° avg, ${t.description ?: ""}, ☔ ${t.rainChancePercent ?: 0}% rain\n")
        }
        sb.append("\n")

        val b = report.bitcoin
        if (b.available) {
            val change = b.change24hPercent ?: 0.0
            sb.append("₿ Bitcoin: €${"%,.0f".format(b.eur ?: 0.0)} (${if (change >= 0) "▲" else "▼"} ${"%.2f".format(kotlin.math.abs(change))}%)\n")
        }
        val c = report.currency
        if (c.available) {
            sb.append("💶 1 ${c.baseCurrency} = ${"%.4f".format(c.rate ?: 0.0)} ${c.targetCurrency}\n")
        }
        sb.append("\n")

        if (report.news.available && report.news.headlines.isNotEmpty()) {
            sb.append("🌍 Top news:\n")
            report.news.headlines.take(10).forEach { h ->
                val src = if (h.source.isNotBlank()) " (${h.source})" else ""
                sb.append("• ${h.title}$src\n")
            }
            if (report.news.headlines.size > 10) {
                sb.append("…and ${report.news.headlines.size - 10} more in the app\n")
            }
        }

        return sb.toString().trim()
    }
}
