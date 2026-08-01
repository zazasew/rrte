package com.morningdigest.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/**
 * Multi-source FX reader.
 *
 * Five independent internet sources are queried in parallel:
 *  1. Yahoo Finance (intraday market quote)
 *  2. Norges Bank (official NOK reference)
 *  3. ECB (official EUR reference)
 *  4. Frankfurter/ECB API (independent API layer)
 *  5. ExchangeRate-API Open Access (daily reference)
 *
 * The app does not blindly trust one provider. It selects the two successful
 * quotes with the smallest relative difference and displays their average.
 * This makes a single stale/broken provider much less likely to create a
 * visibly wrong conversion.
 *
 * Note: only Yahoo is intraday; the central-bank/reference sources update
 * daily. If live market data is unavailable, the consensus still works from
 * the reference sources and never invents a value.
 */
class LiveCurrencyFetcher(private val client: OkHttpClient) {

    data class LiveQuote(
        val rate: Double,
        val previousClose: Double? = null,
        val source: String,
        val timestamp: Instant? = null
    )

    data class ConsensusQuote(
        val rate: Double,
        val change24hPercent: Double?,
        val sources: List<String>,
        val sourceSpreadPercent: Double,
        val changeTodayPercent: Double? = null,
        val updatedAtMillis: Long = System.currentTimeMillis()
    )

    private data class SourceResult(
        val quote: LiveQuote,
        val change24hPercent: Double? = null,
        val changeTodayPercent: Double? = null
    )

    suspend fun fetchConsensus(base: String, target: String): ConsensusQuote? = coroutineScope {
        val jobs = listOf(
            async(Dispatchers.IO) { yahoo(base, target) },
            async(Dispatchers.IO) { norgesBank(base, target) },
            async(Dispatchers.IO) { ecb(base, target) },
            async(Dispatchers.IO) { frankfurter(base, target) },
            async(Dispatchers.IO) { exchangeRateApi(base, target) }
        )
        val results = jobs.awaitAll().filterNotNull()
        if (results.isEmpty()) return@coroutineScope null

        // Find the two quotes that agree most closely. Relative difference is
        // symmetric and behaves sensibly regardless of the currency's scale.
        val pair = if (results.size == 1) {
            listOf(results[0])
        } else {
            results.flatMapIndexed { i, a ->
                results.drop(i + 1).map { b ->
                    val midpoint = (a.quote.rate + b.quote.rate) / 2.0
                    val spread = if (midpoint > 0.0) abs(a.quote.rate - b.quote.rate) / midpoint else Double.POSITIVE_INFINITY
                    Triple(a, b, spread)
                }
            }.minByOrNull { it.third }?.let { listOf(it.first, it.second) } ?: listOf(results[0])
        }

        val rate = pair.map { it.quote.rate }.average()
        val changes = pair.mapNotNull { it.change24hPercent }
        val change = changes.takeIf { it.isNotEmpty() }?.average()
        // Yahoo is the intraday source; use its latest candle open for today's change.
        val todayChange = results.mapNotNull { it.changeTodayPercent }.firstOrNull()
        val spread = if (pair.size == 2) {
            abs(pair[0].quote.rate - pair[1].quote.rate) / rate * 100.0
        } else 0.0

        ConsensusQuote(
            rate = rate,
            change24hPercent = change,
            sources = pair.map { it.quote.source },
            sourceSpreadPercent = spread,
            changeTodayPercent = todayChange,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    /** Backwards-compatible entry point used by older callers. */
    suspend fun fetchQuote(base: String, target: String): LiveQuote? =
        fetchConsensus(base, target)?.let {
            LiveQuote(it.rate, source = it.sources.joinToString(" + "))
        }

    private fun requestText(url: String, accept: String = "application/json"): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) TheBrief/1.1")
            .header("Accept", accept)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("empty response")
        }
    }

    private fun valid(value: Double?): Double? = value?.takeIf { it.isFinite() && it > 0.0 }

    private fun yahoo(base: String, target: String): SourceResult? = runCatching {
        val symbol = "${base.uppercase()}${target.uppercase()}=X"
        val body = requestText("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=5d")
        val result = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val meta = result.getJSONObject("meta")
        val rate = valid(meta.optDouble("regularMarketPrice", Double.NaN)) ?: error("no live price")

        val quote = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
        val closes = quote?.optJSONArray("close")
        val opens = quote?.optJSONArray("open")
        val values = buildList {
            if (closes != null) for (i in 0 until closes.length()) {
                if (!closes.isNull(i)) valid(closes.optDouble(i, Double.NaN))?.let(::add)
            }
        }
        val previous = values.dropLast(1).lastOrNull()
        val change = previous?.let { ((rate - it) / it) * 100.0 }
        val todayOpen = opens?.let { arr ->
            (arr.length() - 1 downTo 0).firstOrNull { i -> !arr.isNull(i) }
                ?.let { i -> valid(arr.optDouble(i, Double.NaN)) }
        }
        val todayChange = todayOpen?.takeIf { it > 0.0 }?.let { ((rate - it) / it) * 100.0 }
        SourceResult(LiveQuote(rate, previous, "Yahoo Finance", Instant.now()), change, todayChange)
    }.getOrNull()

    private fun norgesBank(base: String, target: String): SourceResult? = runCatching {
        // Norges Bank publishes NOK per unit of foreign currency. For EUR/NOK
        // this is directly EUR -> NOK. For non-NOK pairs we derive a cross via NOK.
        val baseNok = norgesNokRate(base) ?: error("no base")
        val targetNok = norgesNokRate(target) ?: error("no target")
        val rate = if (base.equals("NOK", true)) 1.0 / targetNok
        else if (target.equals("NOK", true)) baseNok
        else baseNok / targetNok
        SourceResult(LiveQuote(rate, source = "Norges Bank", timestamp = Instant.now()))
    }.getOrNull()

    private fun norgesNokRate(currency: String): Double? {
        if (currency.equals("NOK", true)) return 1.0
        val url = "https://data.norges-bank.no/api/data/EXR/B.${currency.uppercase()}.NOK.SP?lastNObservations=1&format=sdmx-json&locale=en"
        val json = JSONObject(requestText(url))
        val data = json.optJSONObject("data") ?: return null
        val dataSets = data.optJSONArray("dataSets") ?: return null
        val series = dataSets.optJSONObject(0)?.optJSONObject("series") ?: return null
        val key = series.keys().asSequence().firstOrNull() ?: return null
        val observations = series.optJSONObject(key)?.optJSONObject("observations") ?: return null
        val observation = observations.optJSONArray("0") ?: return null
        return valid(observation.optDouble(0, Double.NaN))
    }

    private fun ecb(base: String, target: String): SourceResult? = runCatching {
        val xml = requestText("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml", "application/xml")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        fun eurRate(code: String): Double {
            if (code.equals("EUR", true)) return 1.0
            val nodes = doc.getElementsByTagNameNS("*", "Cube")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                if (el.getAttribute("currency").equals(code.uppercase(), true)) {
                    return valid(el.getAttribute("rate").toDoubleOrNull()) ?: error("bad ECB rate")
                }
            }
            error("ECB has no $code")
        }
        val rate = eurRate(target) / eurRate(base)
        SourceResult(LiveQuote(rate, source = "European Central Bank", timestamp = Instant.now()))
    }.getOrNull()

    private fun frankfurter(base: String, target: String): SourceResult? = runCatching {
        val json = JSONObject(requestText("https://api.frankfurter.dev/v2/rate/${base.uppercase()}/${target.uppercase()}"))
        val rate = valid(json.optDouble("rate", Double.NaN)) ?: error("no rate")
        SourceResult(LiveQuote(rate, source = "Frankfurter / ECB", timestamp = Instant.now()))
    }.getOrNull()

    private fun exchangeRateApi(base: String, target: String): SourceResult? = runCatching {
        val json = JSONObject(requestText("https://open.er-api.com/v6/latest/${base.uppercase()}"))
        val rates = json.optJSONObject("rates") ?: error("no rates")
        val rate = valid(rates.optDouble(target.uppercase(), Double.NaN)) ?: error("no target rate")
        SourceResult(LiveQuote(rate, source = "ExchangeRate-API", timestamp = Instant.now()))
    }.getOrNull()
}
