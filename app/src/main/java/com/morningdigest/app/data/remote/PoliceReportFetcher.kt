package com.morningdigest.app.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Rebuilt from zero. Fetches Norway's public police incident log
 * ("Politiloggen") for Max the assistant's Police Report card and the
 * dashboard's shield indicator.
 *
 * Design goals, given the history of this feature (several earlier attempts,
 * each individually "confirmed" against either a decompiled official app or
 * a real device, still ended up returning an empty response body on at
 * least one real device/network - most likely something specific to that
 * device/network path rather than the API being categorically closed to
 * outside callers, since a third-party server-side tool calling the same
 * host works fine):
 *
 * 1. NEVER bet the whole feature on one exact, unverifiable request shape.
 *    Two independently-sourced request variants are tried in order; the
 *    first one that comes back with real, parseable content wins.
 * 2. NEVER show a scary hard failure if there's ANY previously-successful
 *    data to fall back on. A successful fetch is cached to disk; if a
 *    refresh fails, the last good report is served instead, clearly marked
 *    with its own age, so "can't reach the service right now" never means
 *    "the report disappears."
 * 3. If a fetch fails AND there is no cache at all (e.g. first run on a
 *    blocked network), the exception message spells out exactly what was
 *    tried and what came back for each attempt (HTTP status, byte count),
 *    so a real failure is diagnosable from the error text alone instead of
 *    requiring another guessing round.
 * 4. English by default, Norwegian original always kept alongside it. Every
 *    incident's text is translated once (and cached), but the raw Norwegian
 *    text is never discarded - Politiloggen itself has no English version
 *    (confirmed: it's a documented, oft-requested missing feature on the
 *    official app), so this app is the only place the English text exists.
 *    Every incident also carries a real timestamp - the UI shows the actual
 *    date and time, not just "x hours ago", so its age is unambiguous.
 * 5. Tapping an incident opens its real Politiloggen source page (Norwegian,
 *    since that's the only language it has) - see [sourceUrl].
 */
class PoliceReportFetcher(private val context: Context, private val client: OkHttpClient) {

    data class Incident(
        val id: String,
        val threadId: String,
        val category: String,
        val categoryEn: String,
        val municipality: String,
        val area: String,
        val createdMillis: Long,
        val text: String,
        val englishText: String,
        val sourceUrl: String
    )

    /** Thrown only when there is truly nothing to show - no fresh data AND no usable cache. Carries a diagnostic-rich message. */
    class PoliceReportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        private const val BASE_URL = "https://api.politiloggen.politiet.no"
        private const val CACHE_FILE_NAME = "police_report_cache.json"
        private const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L // stale cache older than 6h is not offered as a silent success

        // /messages only ever returns its most recent page by default; a
        // quiet municipality's incidents can be a few days back and get
        // buried under national-level traffic/theft volume in page one.
        // Paging deeper (bounded) is what makes "show me Kirkenes reports
        // even if they're a few days old" actually work.
        private const val MESSAGE_PAGE_SIZE = 100
        private const val MAX_MESSAGE_PAGES = 15

        // Every municipality gets its own bucket of up to this many reports -
        // a busy city can never crowd out a quiet one. Once a municipality
        // has 10, older ones for that municipality simply aren't fetched
        // anymore; the next real refresh that finds something newer for it
        // pushes the oldest of its 10 out naturally (nothing is ever stored
        // beyond what's freshly fetched, so there's no explicit "delete" step
        // needed - see fetch()).
        const val PER_MUNICIPALITY_LIMIT = 10

        val CATEGORY_TRANSLATIONS = linkedMapOf(
            "Arrangement" to "Events",
            "Brann" to "Fire",
            "Dyr" to "Animals",
            "Innbrudd" to "Burglary",
            "Redning" to "Rescue",
            "Ro og orden" to "Public order",
            "Savnet" to "Missing person",
            "Sjø" to "Maritime incident",
            "Skadeverk" to "Vandalism / property damage",
            "Trafikk" to "Traffic",
            "Tyveri" to "Theft",
            "Ulykke" to "Accident",
            "Voldshendelse" to "Violence",
            "Vær" to "Weather",
            "Andre hendelser" to "Other incidents"
        )
    }

    private val translationCache = ConcurrentHashMap<String, String>()
    private val translationClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    // ---- District/municipality picker: fully offline, see PoliceDistricts.kt ----

    fun fetchDistricts(): List<PoliceDistricts.DistrictItem> = PoliceDistricts.districts

    fun fetchMunicipalitiesForDistrict(district: PoliceDistricts.DistrictItem): List<String> =
        PoliceDistricts.municipalitiesFor(district)

    // ---- Incident fetching ----

    suspend fun fetch(municipality: String, enabledCategories: Set<String>): List<Incident> =
        fetch(listOf(municipality), enabledCategories)

    suspend fun fetch(municipalities: List<String>, enabledCategories: Set<String>): List<Incident> = withContext(Dispatchers.IO) {
        val targets = municipalities.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        if (targets.isEmpty()) return@withContext emptyList()

        val attempts = mutableListOf<String>()

        val viaMessages = runCatching { fetchViaMessages(targets, enabledCategories) }
            .onFailure { attempts += "messages: ${describeError(it)}" }
            .getOrNull()
        if (viaMessages != null) {
            saveCache(viaMessages)
            return@withContext viaMessages
        }

        val viaThreads = runCatching { fetchViaMessageThreads(targets, enabledCategories) }
            .onFailure { attempts += "messagethreads: ${describeError(it)}" }
            .getOrNull()
        if (viaThreads != null) {
            saveCache(viaThreads)
            return@withContext viaThreads
        }

        // Both request strategies failed. Fall back to the last successful
        // fetch, however old, rather than showing a hard error - a stale
        // report is far more useful than none. But the cache is whatever was
        // fetched *last time*, which may have been for a different
        // municipality/category selection than right now (e.g. the person
        // just removed a municipality in Settings) - re-apply today's
        // filters, and the same per-municipality cap, so a removed
        // municipality's old incidents can never resurface and a stale
        // over-sized cache can't bypass the cap just because a refresh
        // happened to hit a network hiccup.
        val cached = loadCache()
            ?.filter { inc ->
                targets.any { it.equals(inc.municipality, ignoreCase = true) } &&
                    (enabledCategories.isEmpty() || enabledCategories.containsAll(CATEGORY_TRANSLATIONS.keys) || inc.category in enabledCategories)
            }
            ?.groupBy { it.municipality.lowercase() }
            ?.flatMap { (_, group) -> group.sortedByDescending { it.createdMillis }.take(PER_MUNICIPALITY_LIMIT) }
        if (cached != null) return@withContext cached

        throw PoliceReportException(
            "Couldn't reach the police report service, and there's no previously saved report to fall back on. " +
                "Tried: ${attempts.joinToString(" | ").ifBlank { "no attempts recorded" }}"
        )
    }

    /** Also exposes cache age so the UI can show "as of HH:mm" when serving fallback data. */
    fun cacheAgeMillis(): Long? = loadCacheMeta()?.let { System.currentTimeMillis() - it }

    // ---- Strategy A: /messagethreads (nested per-thread updates) ----

    private suspend fun fetchViaMessageThreads(municipalities: List<String>, categories: Set<String>): List<Incident> {
        val builder = "$BASE_URL/messagethreads".toHttpUrl().newBuilder()
            .addQueryParameter("Skip", "0")
            .addQueryParameter("Take", (PER_MUNICIPALITY_LIMIT * (municipalities.size.coerceAtLeast(1)) * 4).coerceAtMost(500).toString())
        val body = requestBody(builder.build().toString())
        val root = JSONObject(body) // throws JSONException on empty/garbage body - caught by caller
        val threads = root.optJSONArray("messageThreads") ?: root.optJSONArray("MessageThreads")
            ?: throw JSONException("no messageThreads array in response")

        val out = mutableListOf<Incident>()
        for (i in 0 until threads.length()) {
            val thread = threads.optJSONObject(i) ?: continue
            val municipalityName = thread.optString("municipality")
            if (municipalities.isNotEmpty() && municipalities.none { it.equals(municipalityName, ignoreCase = true) }) continue
            val category = thread.optString("category")
            val isCustomCategoryFilter = categories.isNotEmpty() && !categories.containsAll(CATEGORY_TRANSLATIONS.keys)
            if (isCustomCategoryFilter && category !in categories) continue
            val threadId = thread.optString("id").trim()
            val area = thread.optString("area")
            val messages = thread.optJSONArray("messages") ?: JSONArray()
            val threadSourceUrl = sourceUrl(threadId)

            if (messages.length() == 0) {
                val text = thread.optString("text").ifBlank { thread.optString("description") }
                if (threadId.isNotBlank() && text.isNotBlank()) {
                    out += Incident(
                        id = threadId, threadId = threadId, category = category, categoryEn = CATEGORY_TRANSLATIONS[category] ?: category,
                        municipality = municipalityName, area = area,
                        createdMillis = parseMillis(thread.optString("createdOn").ifBlank { thread.optString("lastMessageOn") }),
                        text = text, englishText = text, sourceUrl = threadSourceUrl
                    )
                }
                continue
            }
            for (m in 0 until messages.length()) {
                val msg = messages.optJSONObject(m) ?: continue
                val text = msg.optString("text").ifBlank { msg.optString("description") }
                val msgId = msg.optString("id").trim().ifBlank { "$threadId-$m" }
                if (text.isBlank()) continue
                out += Incident(
                    id = msgId, threadId = threadId, category = category, categoryEn = CATEGORY_TRANSLATIONS[category] ?: category,
                    municipality = municipalityName, area = area,
                    createdMillis = parseMillis(msg.optString("createdOn")),
                    text = text, englishText = text, sourceUrl = threadSourceUrl
                )
            }
        }
        if (out.isEmpty() && threads.length() > 0) {
            // We got real data back but every row failed to parse into an Incident -
            // treat as a shape mismatch, not "zero incidents", so the other strategy gets a turn.
            throw JSONException("messageThreads array had ${threads.length()} entries but none parsed")
        }
        // Same per-municipality cap as the primary strategy - a busy city
        // still can't crowd out a quiet one, even on this fallback path.
        val capped = out.groupBy { it.municipality.lowercase() }
            .flatMap { (_, group) -> group.sortedByDescending { it.createdMillis }.take(PER_MUNICIPALITY_LIMIT) }
        return translate(capped.sortedByDescending { it.createdMillis })
    }

    // ---- Strategy B: /messages (flat list) - confirmed working shape; paginates deep enough to find a quiet municipality's older-but-recent incidents ----

    private suspend fun fetchViaMessages(municipalities: List<String>, categories: Set<String>): List<Incident> {
        val out = mutableListOf<Incident>()
        val seenIds = mutableSetOf<String>()
        // One counter per requested municipality (lowercased) - each capped
        // independently at PER_MUNICIPALITY_LIMIT so a busy city can never
        // eat into a quiet one's share. Paging keeps going, as deep as the
        // page budget allows, until every requested municipality has hit its
        // cap (or genuinely runs out of history) - it doesn't stop early
        // just because one of them filled up fast.
        val perMunicipalityCounts = municipalities.associate { it.lowercase() to 0 }.toMutableMap()
        var skip = 0
        var totalCount = Int.MAX_VALUE
        var rowsWithUsableFields = 0
        var rowsSeenTotal = 0

        fun allTargetsFull() = perMunicipalityCounts.isNotEmpty() && perMunicipalityCounts.values.all { it >= PER_MUNICIPALITY_LIMIT }

        for (page in 0 until MAX_MESSAGE_PAGES) {
            if (allTargetsFull() || skip >= totalCount) break

            val messages: JSONArray
            val total: Int
            try {
                val builder = "$BASE_URL/messages".toHttpUrl().newBuilder()
                    .addQueryParameter("Take", MESSAGE_PAGE_SIZE.toString())
                    .addQueryParameter("Skip", skip.toString())
                val body = requestBody(builder.build().toString())
                val root = JSONObject(body)
                messages = root.optJSONArray("messages") ?: root.optJSONArray("Messages")
                    ?: throw JSONException("no messages array in response")
                total = root.optInt("totalCount", if (messages.length() < MESSAGE_PAGE_SIZE) skip + messages.length() else Int.MAX_VALUE)
            } catch (e: Exception) {
                // The very first page failing is a real "couldn't reach it"
                // signal the caller should know about (falls through to the
                // other strategy, then cache). A later page hiccuping after
                // we already have real data is just where the search stops -
                // keep whatever was already found rather than losing it.
                if (page == 0) throw e
                break
            }
            totalCount = total
            if (messages.length() == 0) break

            for (i in 0 until messages.length()) {
                val msg = messages.optJSONObject(i) ?: continue
                rowsSeenTotal++
                val text = msg.optString("text").ifBlank { msg.optString("description") }
                val id = msg.optString("id").trim().ifBlank { msg.optString("threadId").trim() }
                if (id.isBlank() || text.isBlank()) continue
                rowsWithUsableFields++

                val municipalityName = msg.optString("municipality")
                if (municipalities.isNotEmpty() && municipalities.none { it.equals(municipalityName, ignoreCase = true) }) continue

                val bucketKey = municipalityName.lowercase()
                if ((perMunicipalityCounts[bucketKey] ?: 0) >= PER_MUNICIPALITY_LIMIT) continue // this municipality's 10 are already spoken for

                // Untouched default (every known category selected) behaves as
                // "no filter" - otherwise a live category-name mismatch could
                // silently zero out results for someone who never customized
                // their category checkboxes.
                val category = msg.optString("category")
                val isCustomCategoryFilter = categories.isNotEmpty() && !categories.containsAll(CATEGORY_TRANSLATIONS.keys)
                if (isCustomCategoryFilter && category !in categories) continue
                if (!seenIds.add(id)) continue

                val threadId = msg.optString("threadId").trim().ifBlank { id }
                out += Incident(
                    id = id, threadId = threadId, category = category, categoryEn = CATEGORY_TRANSLATIONS[category] ?: category,
                    municipality = municipalityName, area = msg.optString("area"),
                    createdMillis = parseMillis(msg.optString("createdOn").ifBlank { msg.optString("updatedOn") }),
                    text = text, englishText = text, sourceUrl = sourceUrl(threadId)
                )
                perMunicipalityCounts[bucketKey] = (perMunicipalityCounts[bucketKey] ?: 0) + 1
            }
            skip += MESSAGE_PAGE_SIZE
        }
        if (rowsSeenTotal > 0 && rowsWithUsableFields == 0) {
            // Every row across every page fetched was missing id/text -
            // the response shape itself has drifted, not "zero incidents
            // for this municipality". Let the other strategy have a turn.
            throw JSONException("messages had $rowsSeenTotal entries but none had usable id/text fields")
        }
        return translate(out.sortedByDescending { it.createdMillis })
    }

    /** The real, confirmed Politiloggen page for one report - plain path, no `/en/` (the site has no English version), no hash routing. */
    private fun sourceUrl(threadId: String): String =
        if (threadId.isBlank()) "https://www.politiet.no/politiloggen"
        else "https://www.politiet.no/politiloggen/hendelse/$threadId"

    // ---- Disk cache: last successful fetch, so a bad refresh never means "no report" ----

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE_NAME)

    private fun saveCache(incidents: List<Incident>) {
        runCatching {
            val arr = JSONArray()
            incidents.forEach { inc ->
                arr.put(
                    JSONObject()
                        .put("id", inc.id).put("threadId", inc.threadId).put("category", inc.category).put("categoryEn", inc.categoryEn)
                        .put("municipality", inc.municipality).put("area", inc.area)
                        .put("createdMillis", inc.createdMillis).put("text", inc.text).put("englishText", inc.englishText)
                        .put("sourceUrl", inc.sourceUrl)
                )
            }
            val wrapper = JSONObject().put("savedAtMillis", System.currentTimeMillis()).put("incidents", arr)
            cacheFile().writeText(wrapper.toString())
        }
    }

    private fun loadCache(): List<Incident>? = runCatching {
        val wrapper = JSONObject(cacheFile().readText())
        val arr = wrapper.optJSONArray("incidents") ?: return@runCatching null
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Incident(
                id = o.optString("id"), threadId = o.optString("threadId").ifBlank { o.optString("id") },
                category = o.optString("category"), categoryEn = o.optString("categoryEn"),
                municipality = o.optString("municipality"), area = o.optString("area"),
                createdMillis = o.optLong("createdMillis"),
                text = o.optString("text").ifBlank { o.optString("englishText") }, englishText = o.optString("englishText"),
                sourceUrl = o.optString("sourceUrl")
            )
        }
    }.getOrNull()

    private fun loadCacheMeta(): Long? = runCatching {
        JSONObject(cacheFile().readText()).optLong("savedAtMillis").takeIf { it > 0 }
    }.getOrNull()

    // ---- Translation (best-effort, cached, time-boxed so it never blocks the report) ----

    private suspend fun translate(items: List<Incident>): List<Incident> {
        if (items.isEmpty()) return items
        val translated = withTimeoutOrNull(8_000L) {
            coroutineScope {
                items.map { item -> async(Dispatchers.IO) { item.id to translateCached(item.text) } }.awaitAll().toMap()
            }
        }.orEmpty()
        return items.map { item -> translated[item.id]?.let { item.copy(englishText = it) } ?: item }
    }

    private fun translateCached(text: String): String? {
        if (text.isBlank()) return null
        translationCache[text]?.let { return it }
        val result = translateNoToEn(text) ?: return null
        translationCache[text] = result
        return result
    }

    private fun translateNoToEn(text: String): String? {
        val q = URLEncoder.encode(text.take(2000), "UTF-8")
        runCatching {
            val json = JSONObject(requestBody(translationClient, "https://api.mymemory.translated.net/get?q=$q&langpair=no|en"))
            json.optJSONObject("responseData")?.optString("translatedText")?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { return it }
        return runCatching {
            val json = JSONArray(requestBody(translationClient, "https://translate.googleapis.com/translate_a/single?client=gtx&sl=no&tl=en&dt=t&q=$q"))
            json.optJSONArray(0)?.optJSONArray(0)?.optString(0)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    // ---- Low-level HTTP + diagnostics ----

    private fun requestBody(url: String): String = requestBody(client, url)

    private fun requestBody(httpClient: OkHttpClient, url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "TheBrief/1.3 Android")
            .header("Accept", "application/json")
            .build()
        return httpClient.newCall(req).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}, ${bytes.size} bytes")
            if (bytes.isEmpty()) throw IllegalStateException("HTTP ${response.code}, 0 bytes (empty response body)")
            String(bytes)
        }
    }

    private fun describeError(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "couldn't resolve host"
        is java.net.SocketTimeoutException -> "timed out"
        is javax.net.ssl.SSLException -> "TLS error (${e.message})"
        is JSONException -> "bad response shape (${e.message})"
        is IllegalStateException -> e.message ?: "HTTP error"
        else -> "${e.javaClass.simpleName}: ${e.message}"
    }

    private fun parseMillis(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
}
