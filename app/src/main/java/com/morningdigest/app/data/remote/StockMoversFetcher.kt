package com.morningdigest.app.data.remote

import com.morningdigest.app.data.model.StockMover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fetches today's biggest stock-market gainers/losers for Bully/Beary's
 * "biggest winners"/"biggest losers" briefings, using Yahoo Finance's public
 * (no API key required) predefined screener endpoint. Same "continue on
 * fail" philosophy as every other source in this app: a failure here just
 * results in an empty list, it never breaks the rest of the digest.
 */
class StockMoversFetcher(private val client: OkHttpClient) {

    /** [screenerId] is either "day_gainers" or "day_losers". */
    private suspend fun fetchScreener(screenerId: String, count: Int): List<StockMover> =
        withContext(Dispatchers.IO) {
            val url = "https://query1.finance.yahoo.com/v1/finance/screener/predefined/saved" +
                "?formatted=false&lang=en-US&region=US&count=$count&scrIds=$screenerId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) MorningDigest/1.0")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Stock screener HTTP ${response.code}")
                val body = response.body?.string() ?: throw Exception("Empty screener body")
                parse(body).take(count)
            }
        }

    private fun parse(body: String): List<StockMover> {
        val root = JSONObject(body)
        val result = root.optJSONObject("finance")
            ?.optJSONArray("result")
            ?.optJSONObject(0)
            ?: return emptyList()
        val quotes = result.optJSONArray("quotes") ?: return emptyList()
        val movers = mutableListOf<StockMover>()
        for (i in 0 until quotes.length()) {
            val q = quotes.optJSONObject(i) ?: continue
            val symbol = q.optString("symbol", "")
            if (symbol.isBlank()) continue
            val name = q.optString("shortName", q.optString("longName", symbol))
            val price = if (q.has("regularMarketPrice")) q.optDouble("regularMarketPrice") else null
            val change = if (q.has("regularMarketChangePercent")) q.optDouble("regularMarketChangePercent") else null
            movers += StockMover(symbol = symbol, name = name, price = price, changePercent = change)
        }
        return movers
    }

    suspend fun fetchTopGainers(count: Int = 5): List<StockMover> =
        runCatching { fetchScreener("day_gainers", count) }.getOrElse { emptyList() }

    suspend fun fetchTopLosers(count: Int = 5): List<StockMover> =
        runCatching { fetchScreener("day_losers", count) }.getOrElse { emptyList() }
}
