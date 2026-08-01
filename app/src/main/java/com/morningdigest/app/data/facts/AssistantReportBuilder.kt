package com.morningdigest.app.data.facts

import com.morningdigest.app.data.model.DigestReport
import com.morningdigest.app.data.model.NewsHeadline
import com.morningdigest.app.data.model.StockMover
import com.morningdigest.app.data.prefs.MascotCharacter
import kotlin.math.abs
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One analyst's generated briefing. [generatedAtMillis] reflects when the underlying data was last fetched. */
data class AssistantReport(val body: String, val generatedAtMillis: Long)

/**
 * One line in an analyst's fuller detail-screen briefing. [link] is set only
 * for lines that came from an actual news headline, so the detail screen can
 * make just those rows tappable and jump straight to the source article -
 * plain summary/movers lines (weather-style text, price movers, etc.) simply
 * have no link and render as static text.
 */
data class AssistantDetailLine(val text: String, val link: String? = null)

/**
 * Builds each analyst's independent daily briefing purely from data the app
 * already has (weather, live RSS news, Bitcoin/currency/watchlist prices,
 * stock market movers) - no chat, no user interaction, no invented numbers.
 * Topics this app has no real data source for are simply left out rather
 * than fabricated.
 *
 * Every report is a pure function of the current [DigestReport], so calling
 * this again after any partial refresh (weather-only, markets-only, etc.)
 * naturally only changes the text for the analyst whose underlying data
 * actually changed - there's no separate "regenerate" event system needed.
 *
 * Character assignments (per the latest requested layout):
 * - Panda -> Business Analyst (business news)
 * - Scoop (Owl) -> World News
 * - Bully (Bull) -> Bull Market Strategist (today's biggest stock winners + tracked assets up)
 * - Beary (Bear) -> Risk Manager (today's biggest stock losers + tracked assets down + market risk headlines)
 * - Satoshi (Fox) -> Crypto Specialist
 * - Anja (Cat) -> US Politics Analyst (mixed multi-source US politics news)
 */
object AssistantReportBuilder {

    fun build(character: MascotCharacter, report: DigestReport?): AssistantReport {
        val body = when (character) {
            MascotCharacter.PANDA -> buildPandaBusiness(report)
            MascotCharacter.OWL -> buildOwl(report)
            MascotCharacter.BULL -> buildBull(report)
            MascotCharacter.BEAR -> buildBear(report)
            MascotCharacter.FOX -> buildFox(report)
            MascotCharacter.CAT -> buildCatPolitics(report)
            MascotCharacter.MAX -> "Nearby public police incidents are on his Police Report card - tap it for the full list."
        }
        return AssistantReport(body, report?.timestampMillis ?: System.currentTimeMillis())
    }

    private fun buildPandaBusiness(report: DigestReport?): String {
        val headlines = report?.businessNews?.headlines.orEmpty()
        if (headlines.isEmpty()) return "No business news available right now - make sure Business is on in Settings, or pull to refresh."
        val lead = headlines.first().title
        val second = headlines.getOrNull(1)?.title
        return if (second != null) "Worth watching: $lead. Also: $second" else "Worth watching: $lead"
    }

    private fun buildOwl(report: DigestReport?): String {
        val headlines = report?.news?.headlines.orEmpty()
        if (headlines.isEmpty()) return "No world news available right now - make sure World News is on in Settings, or pull to refresh."
        val lead = headlines.first().title
        val second = headlines.getOrNull(1)?.title
        return if (second != null) "Top story right now: $lead. Also developing: $second" else "Top story right now: $lead"
    }

    private fun buildBull(report: DigestReport?): String {
        if (report == null) return "Market data isn't available yet - pull to refresh."
        val parts = mutableListOf<String>()
        val gainers = report.stockGainers
        if (gainers.isNotEmpty()) {
            val top = gainers.first()
            val topChange = top.changePercent
            parts += if (topChange != null) {
                "Today's biggest winner is ${top.symbol} (${top.name}), up ${"%.2f".format(topChange)}%."
            } else {
                "Today's biggest winner is ${top.symbol} (${top.name})."
            }
            if (gainers.size > 1) {
                val rest = gainers.drop(1).take(4).joinToString(", ") { m ->
                    m.changePercent?.let { "${m.symbol} +${"%.2f".format(it)}%" } ?: m.symbol
                }
                parts += "Also up today: $rest."
            }
        } else {
            val movers = collectMovers(report)
            val best = movers.maxByOrNull { it.second }
            parts += when {
                best != null && best.second > 0 ->
                    "${best.first} is up ${"%.2f".format(best.second)}% today - momentum looks constructive."
                best != null ->
                    "Things are pulling back a little across the board today, but the bigger trend still looks fine - good setups take patience."
                else -> "No tracked assets to report on yet - add a currency or crypto pair in Settings to get a real read on this."
            }
        }
        report.marketsNews.headlines.firstOrNull()?.let { parts += "In the news: ${it.title}" }
        return parts.joinToString(" ")
    }

    private fun buildBear(report: DigestReport?): String {
        if (report == null) return "Market data isn't available yet - pull to refresh."
        val parts = mutableListOf<String>()
        val losers = report.stockLosers
        if (losers.isNotEmpty()) {
            val top = losers.first()
            val topChange = top.changePercent
            parts += if (topChange != null) {
                "Today's biggest loser is ${top.symbol} (${top.name}), down ${"%.2f".format(abs(topChange))}%."
            } else {
                "Today's biggest loser is ${top.symbol} (${top.name})."
            }
            if (losers.size > 1) {
                val rest = losers.drop(1).take(4).joinToString(", ") { m ->
                    m.changePercent?.let { "${m.symbol} ${"%.2f".format(it)}%" } ?: m.symbol
                }
                parts += "Also down today: $rest."
            }
        } else {
            val movers = collectMovers(report)
            val worst = movers.minByOrNull { it.second }
            parts += if (worst != null && worst.second < 0) {
                "${worst.first} is down ${"%.2f".format(abs(worst.second))}% today - worth keeping an eye on."
            } else {
                "Nothing alarming in your tracked assets today, but conditions can shift fast - keep your risk limits in mind regardless."
            }
        }
        report.marketsNews.headlines.firstOrNull()?.let { parts += "Also in the news: ${it.title}" }
        return parts.joinToString(" ")
    }

    private fun buildFox(report: DigestReport?): String {
        if (report == null) return "Crypto data isn't available yet - pull to refresh."
        val parts = mutableListOf<String>()
        val b = report.bitcoin
        if (b.available) {
            val change = b.change24hPercent ?: 0.0
            parts += "Bitcoin is ${if (change >= 0) "up" else "down"} ${"%.2f".format(abs(change))}% today, around €${"%,.0f".format(b.eur ?: 0.0)}."
        }
        report.watchlist.filter { it.isCrypto && it.available && it.change24hPercent != null }.take(2).forEach { entry ->
            val change = entry.change24hPercent!!
            parts += "${entry.label} is ${if (change >= 0) "up" else "down"} ${"%.2f".format(abs(change))}%."
        }
        report.cryptoNews.headlines.firstOrNull()?.let { parts += "Crypto headline: ${it.title}" }
        if (parts.isEmpty()) return "No crypto data to report on yet - check your Bitcoin and watchlist settings."
        return parts.joinToString(" ")
    }

    private fun buildCatPolitics(report: DigestReport?): String {
        val headlines = report?.politicsNews?.headlines.orEmpty()
        if (headlines.isEmpty()) return "No US politics news available right now - make sure US Politics is on in Settings, or pull to refresh."
        val lead = headlines.first().title
        val second = headlines.getOrNull(1)?.title
        return if (second != null) "Top story right now: $lead. Also developing: $second" else "Top story right now: $lead"
    }

    /** Every tracked price-moving asset (Bitcoin, main currency pair, watchlist entries) as (label, change%) pairs. */
    private fun collectMovers(report: DigestReport): List<Pair<String, Double>> {
        val list = mutableListOf<Pair<String, Double>>()
        if (report.bitcoin.available) report.bitcoin.change24hPercent?.let { list += "Bitcoin" to it }
        if (report.currency.available) {
            report.currency.change24hPercent?.let {
                list += "${report.currency.baseCurrency}/${report.currency.targetCurrency}" to it
            }
        }
        report.watchlist.forEach { entry ->
            if (entry.available) entry.change24hPercent?.let { list += entry.label to it }
        }
        return list
    }

    /**
     * A fuller, multi-line version of each report for the dedicated
     * per-character detail screen - more headlines/movers than the one-line
     * dashboard summary, not just a single sentence. Each returned line is
     * one row in that screen's list; lines with a non-null [AssistantDetailLine.link]
     * are tappable and open the source article.
     */
    fun buildDetailLines(character: MascotCharacter, report: DigestReport?): List<AssistantDetailLine> {
        if (report == null) return listOf(AssistantDetailLine("No data available yet - pull to refresh."))
        return when (character) {
            MascotCharacter.PANDA -> detailNewsList(report.businessNews.headlines, "business news")
            MascotCharacter.OWL -> detailNewsList(report.news.headlines, "world news")
            MascotCharacter.BULL -> detailStockMovers(report.stockGainers, ascending = false) +
                detailMovers(report, ascending = false) +
                detailNewsList(report.marketsNews.headlines, "markets news", asHeader = false)
            MascotCharacter.BEAR -> detailStockMovers(report.stockLosers, ascending = true) +
                detailMovers(report, ascending = true) +
                detailNewsList(report.marketsNews.headlines, "markets news", asHeader = false)
            MascotCharacter.FOX -> detailFox(report)
            MascotCharacter.CAT -> detailNewsList(report.politicsNews.headlines, "US politics news")
            // Max's detail screen is special-cased in AssistantDetailScreen to show
            // the live police incident list directly (it isn't part of DigestReport),
            // so this branch is never actually rendered - kept only so `when` stays exhaustive.
            MascotCharacter.MAX -> emptyList()
        }
    }

    private fun detailStockMovers(movers: List<StockMover>, ascending: Boolean): List<AssistantDetailLine> {
        if (movers.isEmpty()) return emptyList()
        val label = if (ascending) "Biggest losers today" else "Biggest winners today"
        val header = AssistantDetailLine(label)
        val rows = movers.map { m ->
            val change = m.changePercent
            val changeText = if (change != null) "${if (change >= 0) "▲" else "▼"} ${"%.2f".format(abs(change))}%" else ""
            val priceText = m.price?.let { " · $${"%.2f".format(it)}" } ?: ""
            AssistantDetailLine("${m.symbol} — ${m.name} $changeText$priceText".trim())
        }
        return listOf(header) + rows
    }

    private fun detailMovers(report: DigestReport, ascending: Boolean): List<AssistantDetailLine> {
        val movers = collectMovers(report)
        if (movers.isEmpty()) return listOf(AssistantDetailLine("No tracked assets yet - add a currency or crypto pair in Settings."))
        val sorted = if (ascending) movers.sortedBy { it.second } else movers.sortedByDescending { it.second }
        return sorted.map { (label, change) ->
            AssistantDetailLine("${if (change >= 0) "▲" else "▼"} $label: ${"%.2f".format(abs(change))}%")
        }
    }

    private fun detailFox(report: DigestReport): List<AssistantDetailLine> {
        val lines = mutableListOf<AssistantDetailLine>()
        val b = report.bitcoin
        if (b.available) {
            val change = b.change24hPercent ?: 0.0
            lines += AssistantDetailLine("${if (change >= 0) "▲" else "▼"} Bitcoin: ${"%.2f".format(abs(change))}% (€${"%,.0f".format(b.eur ?: 0.0)})")
        }
        report.watchlist.filter { it.isCrypto && it.available }.forEach { entry ->
            val change = entry.change24hPercent
            if (change != null) lines += AssistantDetailLine("${if (change >= 0) "▲" else "▼"} ${entry.label}: ${"%.2f".format(abs(change))}%")
        }
        if (lines.isEmpty()) lines += AssistantDetailLine("No crypto data yet - check your Bitcoin and watchlist settings.")
        lines += detailNewsList(report.cryptoNews.headlines, "crypto news", asHeader = false)
        return lines
    }

    private fun detailNewsList(headlines: List<NewsHeadline>, label: String, asHeader: Boolean = true): List<AssistantDetailLine> {
        if (headlines.isEmpty()) {
            return if (asHeader) listOf(AssistantDetailLine("No $label available right now - pull to refresh.")) else emptyList()
        }
        val timeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return headlines.map { h ->
            val time = h.pubDateMillis?.let { timeFormat.format(Date(it)) }
            val meta = listOfNotNull(h.source.takeIf { it.isNotBlank() }, time).joinToString(" · ")
            AssistantDetailLine(
                text = "${h.title}${if (meta.isNotBlank()) " — $meta" else ""}",
                link = h.link.takeIf { it.isNotBlank() }
            )
        }
    }
}
